"""Private, device-scoped feedback conversations. No GitHub credentials or API calls."""
import socket, ssl, hashlib, hmac, json, os, secrets, sqlite3, time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
PREFIX='/android-agent-feedback'
class Error(Exception):
 def __init__(self,status,code):self.status,self.code=status,code
def digest(value):return hashlib.sha256(value.encode()).hexdigest()

class Backend:
 def __init__(self,root):
  self.root=Path(root);self.root.mkdir(parents=True,exist_ok=True)
  self.db=sqlite3.connect(self.root/'feedback.sqlite');self.db.row_factory=sqlite3.Row
  self.db.executescript('''CREATE TABLE IF NOT EXISTS devices(token TEXT PRIMARY KEY);
  CREATE TABLE IF NOT EXISTS feedback(id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, appVersion TEXT NOT NULL, androidVersion TEXT NOT NULL, status TEXT NOT NULL, created REAL NOT NULL, updated REAL NOT NULL);
  CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT, feedback INTEGER NOT NULL, author TEXT NOT NULL, body TEXT NOT NULL, version TEXT, downloadUrl TEXT, created REAL NOT NULL);
  CREATE TABLE IF NOT EXISTS mutations(device TEXT NOT NULL, request TEXT NOT NULL, payload TEXT NOT NULL, result TEXT NOT NULL, created REAL NOT NULL, PRIMARY KEY(device,request));''');self.db.commit();self.attempts=[]
 def own(self,device,number):
  if type(number)!=int or not self.db.execute('SELECT 1 FROM feedback WHERE id=? AND device=?',(number,device)).fetchone():raise Error(404,'FEEDBACK_NOT_FOUND')
 def handle(self,path,auth,data):
  if path==PREFIX+'/pair':
   now=time.time();self.attempts=[t for t in self.attempts if now-t<60]
   if len(self.attempts)>=10:raise Error(429,'PAIR_RATE_LIMIT')
   self.attempts.append(now)
   try:pair=json.loads((self.root/'pair.json').read_text())
   except (OSError,ValueError):raise Error(403,'PAIR_UNAVAILABLE')
   code=data.get('code','')
   if not isinstance(code,str) or pair['expires']<now or not hmac.compare_digest(digest(code),pair['hash']):raise Error(403,'PAIR_INVALID')
   token=secrets.token_urlsafe(32);device=pair.get('device',digest(token))
   # A recovery code can migrate this owner's existing conversations to a new token.
   with self.db:
    self.db.execute('INSERT INTO devices VALUES (?)',(digest(token),))
    if device!=digest(token):
     self.db.execute('UPDATE feedback SET device=? WHERE device=?',(digest(token),device))
     self.db.execute('UPDATE mutations SET device=? WHERE device=?',(digest(token),device))
     self.db.execute('DELETE FROM devices WHERE token=?',(device,))
   (self.root/'pair.json').unlink();return {'token':token}
  device=digest(auth.removeprefix('Bearer '))
  if not auth.startswith('Bearer ') or not self.db.execute('SELECT 1 FROM devices WHERE token=?',(device,)).fetchone():raise Error(401,'DEVICE_NOT_REGISTERED')
  if path==PREFIX+'/revoke':
   with self.db:self.db.execute('DELETE FROM devices WHERE token=?',(device,))
   return {'revoked':True}
  if path==PREFIX+'/list':
   before=data.get('before',9223372036854775807)
   if type(before)!=int or before<1:raise Error(400,'INVALID_CURSOR')
   rows=self.db.execute('SELECT id,title,category,status,updated FROM feedback WHERE device=? AND id<? ORDER BY id DESC LIMIT 51',(device,before)).fetchall()
   return {'feedback':[dict(row) for row in rows[:50]],'nextBefore':rows[49]['id'] if len(rows)>50 else None}
  if path==PREFIX+'/read':
   number=data.get('feedbackId');self.own(device,number);after=data.get('after',0)
   if type(after)!=int or after<0:raise Error(400,'INVALID_CURSOR')
   item=dict(self.db.execute('SELECT id,title,category,status,appVersion,androidVersion,created,updated FROM feedback WHERE id=?',(number,)).fetchone())
   rows=self.db.execute('SELECT id,author,body,version,downloadUrl,created FROM messages WHERE feedback=? AND id>? ORDER BY id LIMIT 11',(number,after)).fetchall()
   item['messages']=[dict(row) for row in rows[:10]];item['nextAfter']=rows[9]['id'] if len(rows)>10 else None;return item
  if path not in (PREFIX+'/submit',PREFIX+'/reply'):raise Error(404,'NOT_FOUND')
  submit=path.endswith('/submit');required={'requestId','title','body','category','appVersion','androidVersion'} if submit else {'requestId','feedbackId','body'}
  if set(data)!=required:raise Error(400,'INVALID_FIELDS')
  limits=[('requestId',64),('body',6000)]+([('title',120),('category',16),('appVersion',32),('androidVersion',32)] if submit else [])
  for field,limit in limits:
   if not isinstance(data[field],str) or not 1<=len(data[field].strip())<=limit:raise Error(400,'INVALID_'+field.upper())
  if submit and data['category'] not in ('bug','improvement'):raise Error(400,'INVALID_CATEGORY')
  if not submit:self.own(device,data['feedbackId'])
  payload=json.dumps(data,sort_keys=True,ensure_ascii=False);key=('submit:' if submit else 'reply:')+data['requestId']
  old=self.db.execute('SELECT payload,result FROM mutations WHERE device=? AND request=?',(device,key)).fetchone()
  if old:
   if old['payload']!=payload:raise Error(409,'REQUEST_CONFLICT')
   return json.loads(old['result'])
  count=self.db.execute('SELECT count(*) FROM mutations WHERE device=? AND created>?',(device,time.time()-86400)).fetchone()[0]
  if count>=100:raise Error(429,'DAILY_LIMIT')
  now=time.time()
  with self.db:
   if submit:
    cursor=self.db.execute('INSERT INTO feedback(device,title,category,appVersion,androidVersion,status,created,updated) VALUES (?,?,?,?,?,?,?,?)',(device,data['title'],data['category'],data['appVersion'],data['androidVersion'],'received',now,now));number=cursor.lastrowid
   else:number=data['feedbackId'];self.db.execute("UPDATE feedback SET updated=?,status=CASE WHEN status='needs_reply' THEN 'received' ELSE status END WHERE id=?",(now,number))
   message=self.db.execute('INSERT INTO messages(feedback,author,body,created) VALUES (?,?,?,?)',(number,'user',data['body'],now)).lastrowid
   result={'status':'accepted','feedbackId':number,'messageId':message}
   self.db.execute('INSERT INTO mutations VALUES (?,?,?,?,?)',(device,key,payload,json.dumps(result),now))
  return result

class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):self.reply(200 if self.path==PREFIX+'/health' else 404,{'status':'ready'} if self.path==PREFIX+'/health' else {'error':'NOT_FOUND'})
 def do_POST(self):
  try:
   length=int(self.headers.get('Content-Length','0'))
   if not 0<length<=32768:raise Error(413,'PAYLOAD_SIZE')
   self.connection.settimeout(10);data=json.loads(self.rfile.read(length))
   if not isinstance(data,dict):raise Error(400,'INVALID_JSON')
   self.reply(200,self.server.backend.handle(self.path,self.headers.get('Authorization',''),data))
  except Error as error:self.reply(error.status,{'error':error.code})
  except (ValueError,TimeoutError):self.reply(400,{'error':'INVALID_JSON'})
  except Exception:self.reply(500,{'error':'SERVER_ERROR'})
 def reply(self,status,data):
  raw=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(raw)
if __name__=='__main__':
 socket.setdefaulttimeout(10)
 server=HTTPServer(('0.0.0.0',8080),Handler);server.backend=Backend(os.environ['STATE_DIR'])
 tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.minimum_version=ssl.TLSVersion.TLSv1_2;tls.load_cert_chain('/secrets/tls.crt','/secrets/tls.key');server.socket=tls.wrap_socket(server.socket,server_side=True);server.serve_forever()
