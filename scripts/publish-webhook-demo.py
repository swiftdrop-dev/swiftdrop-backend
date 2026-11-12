"""Publish a demo status event via the LOCAL RabbitMQ management API."""
import base64
import json
import uuid
from datetime import datetime, timezone
from urllib.request import Request, urlopen

event = {'eventId': str(uuid.uuid4()), 'event': 'parcel.status.changed',
         'merchantId': 'dev-nathasha', 'trackingNumber': 'SD-BROKER-TEST',
         'merchantOrderRef': 'BROKER-TEST', 'status': 'DELIVERED',
         'timestamp': datetime.now(timezone.utc).isoformat(), 'test': True}
body = {'properties': {'content_type': 'application/json', 'delivery_mode': 2},
        'routing_key': 'parcel.status.changed', 'payload': json.dumps(event), 'payload_encoding': 'string'}
request = Request('http://127.0.0.1:15672/api/exchanges/%2F/swiftdrop.events.local/publish',
                  data=json.dumps(body).encode(), method='POST',
                  headers={'Content-Type': 'application/json',
                           'Authorization': 'Basic ' + base64.b64encode(b'guest:guest').decode()})
with urlopen(request, timeout=10) as response:
    result = json.load(response)
if not result.get('routed'):
    raise SystemExit('Event not routed. Start webhook-service with WEBHOOK_BROKER_ENABLED=true first.')
print('Published demo event:', event['eventId'])
