# Protocol v1 (HTTP, LAN)
All requests carry header `X-Token: <token of the receiving device>`.

- GET  /info            -> {"name","type"}
- POST /pair            body {"name","host","port","token"}  (sender tells receiver who it is)
- POST /send?name=<url-encoded file name>   body = raw file bytes (streamed)

QR payload (JSON): {"v":1,"name","host","port","token"}
