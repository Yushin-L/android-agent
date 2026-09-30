import json
import sys

def send(value):
    print(json.dumps(value), flush=True)

waiting = []
for line in sys.stdin:
    msg = json.loads(line)
    method = msg.get('method')
    if method == 'parallel':
        waiting.append(msg)
        if len(waiting) == 2:
            for request in reversed(waiting):
                send({'id': request['id'], 'result': request['params']})
    elif method == 'tool':
        waiting = [msg]
        send({'id': 'server-request-1', 'method': 'item/tool/call', 'params': {'threadId': 'thread-a', 'turnId': 'turn-a'}})
    elif msg.get('id') == 'server-request-1':
        send({'method': 'turn/completed', 'params': {'threadId': 'thread-a', 'turn': {'id': 'turn-a', 'status': 'completed'}}})
        send({'id': waiting[0]['id'], 'result': msg['result']})
    elif method == 'overlap':
        overlap = msg['id']
        send({'id': 'question', 'method': 'question', 'params': {}})
        send({'id': 'battery', 'method': 'battery', 'params': {}})
    elif msg.get('id') == 'question':
        send({'id': overlap, 'result': msg['result']})
    elif msg.get('id') == 'battery':
        pass
    elif method == 'failure':
        send({'id': msg['id'], 'error': {'code': -32603, 'message': 'PRIVATE_TOKEN_NOT_FOR_UI'}})
    elif method == 'exit':
        sys.exit(0)
