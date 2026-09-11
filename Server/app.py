"""
局域网电量监控 —— Flask 服务器

职责:
  1. 接收手机注册(记录设备名、端口,IP 从请求里自动获取)
  2. 被触发时(打开网页 / 调 /devices)现场并发去各手机拉取实时电量
  3. 提供一个自动刷新的网页仪表盘

运行:
  pip install -r requirements.txt
  python app.py
然后浏览器打开  http://<本机局域网IP>:5010
"""

import time
import threading
from concurrent.futures import ThreadPoolExecutor

import requests
from flask import Flask, request, jsonify, render_template

app = Flask(__name__)

# 内存里存注册的设备。key = deviceId
# value = { name, ip, port, registered_at }
DEVICES = {}
LOCK = threading.Lock()

# 手机端 /battery 接口的超时(秒)。局域网内 3 秒足够
QUERY_TIMEOUT = 3


@app.route("/register", methods=["POST"])
def register():
    """手机上线时调用。IP 由服务器从请求中自动读取。"""
    data = request.get_json(force=True, silent=True) or {}
    device_id = data.get("deviceId")
    if not device_id:
        return jsonify({"ok": False, "error": "deviceId required"}), 400

    with LOCK:
        DEVICES[device_id] = {
            "name": data.get("name", device_id),
            "ip": request.remote_addr,          # 关键:自动取手机局域网 IP
            "port": int(data.get("port", 8080)),
            "registered_at": time.time(),
        }
    app.logger.info("registered %s -> %s:%s",
                    device_id, request.remote_addr, data.get("port"))
    return jsonify({"ok": True, "ip": request.remote_addr})


@app.route("/unregister", methods=["POST"])
def unregister():
    data = request.get_json(force=True, silent=True) or {}
    with LOCK:
        DEVICES.pop(data.get("deviceId", ""), None)
    return jsonify({"ok": True})


def _query_one(device_id, info):
    """去单台手机拉一次电量。失败则标记离线。"""
    url = f"http://{info['ip']}:{info['port']}/battery"
    result = {
        "deviceId": device_id,
        "name": info["name"],
        "ip": info["ip"],
        "online": False,
        "battery": None,
        "charging": None,
    }
    try:
        # 局域网请求要绕开系统代理(有些代理工具把 HTTP_PROXY 等环境变量永久写进了系统变量,
        # 光传 proxies={...: None} 挡不住,必须连 trust_env 一起关掉,requests 才会彻底忽略代理)
        session = requests.Session()
        session.trust_env = False
        r = session.get(url, timeout=QUERY_TIMEOUT, proxies={"http": None, "https": None})
        r.raise_for_status()
        payload = r.json()
        result["online"] = True
        result["battery"] = payload.get("battery")
        result["charging"] = payload.get("charging")
        result["name"] = payload.get("name", info["name"])
    except Exception as e:
        result["error"] = str(e)
        app.logger.warning("query %s (%s) failed: %s", device_id, url, e)
    return result


@app.route("/devices")
def devices():
    """触发点:每次调用都现场并发去所有手机拉一次实时电量。"""
    with LOCK:
        snapshot = list(DEVICES.items())

    if not snapshot:
        return jsonify([])

    with ThreadPoolExecutor(max_workers=min(16, len(snapshot))) as pool:
        results = list(pool.map(lambda kv: _query_one(kv[0], kv[1]), snapshot))

    # 电量低的排前面,离线的排最后
    results.sort(key=lambda d: (not d["online"],
                                d["battery"] if d["battery"] is not None else 999))
    return jsonify(results)


@app.route("/")
def index():
    return render_template("index.html")


if __name__ == "__main__":
    # host=0.0.0.0 让局域网内其他设备能访问
    app.run(host="0.0.0.0", port=5010, debug=False, threaded=True)
