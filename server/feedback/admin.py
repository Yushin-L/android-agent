"""Local operator CLI. No public administrative API."""
import argparse,json,time,secrets
from pathlib import Path
from server import Backend,digest
p=argparse.ArgumentParser();p.add_argument('--state',required=True)
s=p.add_subparsers(dest='action',required=True)
l=s.add_parser('list');l.add_argument('--limit',type=int,default=50)
r=s.add_parser('read');r.add_argument('id',type=int)
c=s.add_parser('comment');c.add_argument('id',type=int);c.add_argument('--body-file',required=True);c.add_argument('--status',choices=['received','in_progress','needs_reply','released','closed'],required=True);c.add_argument('--version');c.add_argument('--download-url')
r=s.add_parser('pair');r.add_argument('--recover-feedback',type=int)
a=p.parse_args();b=Backend(a.state)
if a.action=='list':
 print(json.dumps([dict(row) for row in b.db.execute('SELECT id,title,status,updated FROM feedback ORDER BY updated DESC LIMIT ?',(max(1,min(a.limit,200)),))],ensure_ascii=False))
elif a.action=='read':
 print(json.dumps([dict(row) for row in b.db.execute('SELECT id,author,body,version,downloadUrl,created FROM messages WHERE feedback=? ORDER BY id',(a.id,))],ensure_ascii=False))
elif a.action=='pair':
 code=secrets.token_urlsafe(18);data={'hash':digest(code),'expires':time.time()+86400}
 if a.recover_feedback:
  row=b.db.execute('SELECT device FROM feedback WHERE id=?',(a.recover_feedback,)).fetchone()
  if not row:raise SystemExit('Feedback not found')
  data['device']=row['device']
 file=Path(a.state)/'pair.json';file.write_text(json.dumps(data));file.chmod(0o600);print(code)
else:
 text=Path(a.body_file).read_text()
 if not 1<=len(text.strip())<=6000:raise SystemExit('Invalid message size')
 if not b.db.execute('SELECT 1 FROM feedback WHERE id=?',(a.id,)).fetchone():raise SystemExit('Feedback not found')
 if a.status=='released':
  import re
  if not a.version or not a.download_url or not re.fullmatch(r'http://140\.245\.79\.96/android-agent/android-agent-[0-9.]+-arm64\.apk',a.download_url):raise SystemExit('Release version and allowlisted APK URL required')
 with b.db:
  b.db.execute('INSERT INTO messages(feedback,author,body,version,downloadUrl,created) VALUES (?,?,?,?,?,?)',(a.id,'developer',text,a.version,a.download_url,time.time()))
  b.db.execute('UPDATE feedback SET status=?,updated=? WHERE id=?',(a.status,time.time(),a.id))
 print('saved')
