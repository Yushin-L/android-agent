import tempfile,unittest,json,time
from pathlib import Path
from server import Backend,Error,digest,PREFIX
class FeedbackTest(unittest.TestCase):
 def test_flow(self):
  with tempfile.TemporaryDirectory() as d:
   b=Backend(d)
   def pair(code):
    (Path(d)/'pair.json').write_text(json.dumps({'hash':digest(code),'expires':time.time()+60}));return b.handle(PREFIX+'/pair','',{'code':code})['token']
   with self.assertRaises(Error):b.handle(PREFIX+'/list','',{})
   token=pair('one');auth='Bearer '+token
   with self.assertRaises(Error):b.handle(PREFIX+'/pair','',{'code':'one'})
   data=dict(requestId='1',title='title',body='body',category='bug',appVersion='1',androidVersion='16')
   result=b.handle(PREFIX+'/submit',auth,data);number=result['feedbackId'];self.assertEqual(result,b.handle(PREFIX+'/submit',auth,data))
   with self.assertRaises(Error):b.handle(PREFIX+'/submit',auth,dict(data,body='different'))
   with self.assertRaises(Error):b.handle(PREFIX+'/submit',auth,dict(data,extra='forbidden'))
   self.assertEqual(len(b.handle(PREFIX+'/list',auth,{})['feedback']),1)
   other='Bearer '+pair('two');self.assertEqual(b.handle(PREFIX+'/list',other,{})['feedback'],[])
   with self.assertRaises(Error):b.handle(PREFIX+'/read',other,{'feedbackId':number})
   with self.assertRaises(Error):b.handle(PREFIX+'/reply',other,{'requestId':'x','feedbackId':number,'body':'intrusion'})
   reply=dict(requestId='2',feedbackId=number,body='answer')
   receipt=b.handle(PREFIX+'/reply',auth,reply);self.assertEqual(receipt,b.handle(PREFIX+'/reply',auth,reply))
   self.assertEqual(len(b.handle(PREFIX+'/read',auth,{'feedbackId':number})['messages']),2)
   b=Backend(d);self.assertEqual(b.handle(PREFIX+'/submit',auth,data),result)
   b.handle(PREFIX+'/revoke',auth,{})
   with self.assertRaises(Error):b.handle(PREFIX+'/list',auth,{})
if __name__=='__main__':unittest.main()
