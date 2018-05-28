package zslack ;

import com.ibm.jzos.* ;
import java.io.* ;
import java.util.* ;
import java.util.zip.* ;
import java.net.* ;

public class SLSendMainB {
	public String encoding_in = "Cp939" ;
	public String encoding_out = "UTF-8" ;
	public int limitsize = 0 ;
	public String _RC = null ;
	SLProperties p = null ;

	public static String crlf = "\r\n" ;

	public static void main(String[] args) throws Exception {
		(new SLSendMainB()).main2(args) ;
	}
	public void main2(String[] args) throws Exception {
		checkArgs(args) ;
		ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
		ZipOutputStream zos = new ZipOutputStream(baos) ;
		String _FILTER = null ;
		StringBuilder _filter_res = null ;
		if (!p.isNull("SLACK_WEBHOOK")&&!p.isNull("SLACK_WEBHOOK_REGEX")){
			_FILTER = ".*"+p.getp("SLACK_WEBHOOK_REGEX")+".*" ;
			_filter_res = new StringBuilder(1024) ;
		}

		for(int i=0;i<p._logs.size(); i++) {
			String s = (String)p._logs.get(i) ;
			if (s.endsWith(".txt")) {
				zos.putNextEntry(new ZipEntry(s)) ;
			}else {
				zos.putNextEntry(new ZipEntry(s+".txt")) ;
			}
			ZFile zf = new ZFile("//DD:WORK"+(i+1), "rb,type=record,noseek") ;
			byte[] recbuf = new byte[zf.getLrecl()] ;
			int nread = 0 ;
			String line = null ;
			boolean flag = false ;
			while((nread = zf.read(recbuf)) > 0) {
				while(nread>0 && recbuf[nread-1] == (byte)0x40) nread-- ;
				line = new String(recbuf,0,nread,encoding_in) ;
				zos.write((line+"\n").getBytes(encoding_out)) ;
				if (_FILTER!=null&&line.matches(_FILTER)) {
					if (!flag) { flag = true ; _filter_res.append("```") ;}
					_filter_res.append(line).append("\n") ;
				}
			}
			if (flag) { flag = false ; _filter_res.append("```\n") ;}
			zf.close() ;
		}
		zos.close() ;
		senderB(p.getp("SLACK_CHANNEL"), "archives.zip", p.getp("TITLE"), p.getp("COMMENT"), baos.toByteArray()) ;
		if (_filter_res != null) {
			System.out.println("--webhook--") ;
			if (_filter_res.length() > 0) {
				String ret = SLSimplesend.send(_filter_res.toString()) ;
				System.out.println(ret) ;
			}else {
				System.out.println(" no matching lines: [filter="+_FILTER+"]") ;
			}
		}
	}

	public void senderB(String channels, String filename, String title, String comment, byte[] buf) throws Exception {
		String boundary = makeBoundary() ;
		OutputStream os = null ;
		if (filename == null) filename = "archive" ;
		if (title == null) title = "Message from z/OS" ;
		if (comment == null) comment ="" ;
		if (channels == null) { System.err.println("CHANNEL is undefined..") ;System.exit(0);}

		String[] prop_name, prop_body ;
		prop_name = new String[]{"token", "filename", "filetype", "channels", "title", "initial_comment"} ;
		prop_body = new String[]{p.getp("SLACK_TOKEN"), filename, "zip", channels, title, comment} ;

		URL _url = new URL(p.getp("SLACK_FILE_URL")) ;
		HttpURLConnection con = null ;

		int size = 0 ;
		ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
		size = createbody_text(baos, prop_name, prop_body, boundary) ;
		size += createbody_zip(baos, buf, boundary) ;
		FileOutputStream fos = new FileOutputStream("/tmp/tomo/baosout.txt") ;
		fos.write(baos.toByteArray()) ;
		fos.close() ;

		con = (HttpURLConnection)_url.openConnection() ;
		con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary) ;
		con.setDoInput(true) ;
		con.setDoOutput(true) ;
		con.setRequestMethod("POST") ;

		con.setFixedLengthStreamingMode(baos.size()) ;
		con.connect() ;
		os = con.getOutputStream() ;
		os.write(baos.toByteArray()) ;

		int rc = con.getResponseCode() ;
		String rs = con.getResponseMessage() ;
		BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), encoding_out)) ;
		System.out.println(">>> RESULT: RC="+rc+": " +rs) ;
		String line = null ;
		while((line = reader.readLine()) != null) {
			System.out.println(line) ;
		}
		con.disconnect() ;
	}

	public int createbody_text(OutputStream os, String[] prop_name, String[] prop_body, String boundary) throws Exception {
		int total = 0 ;
		for(int i=0;i<prop_name.length;i++) {
			total += writestr(os, "--" + boundary + crlf) ;
			total += writestr(os, "Content-Disposition: form-data; name=\""+prop_name[i]+"\""+crlf+crlf) ;
			byte[] btmp = prop_body[i].getBytes(encoding_out) ;
			os.write(btmp) ;
			total += btmp.length ;
			total += writestr(os, crlf) ;
		}
		return total ;
	}

	public int createbody_zip(OutputStream os, byte[] buf, String boundary) throws Exception {
		int total = 0 ;
		total += writestr(os, "--" + boundary + crlf) ;
		total += writestr(os, "Content-Disposition: form-data; name=\"file\"; filename=\"archives.zip\""+crlf) ;
		total += writestr(os, "Content-Type: application/zip"+crlf+crlf) ;

		os.write(buf) ;
		total += buf.length ;
		total += writestr(os, crlf + "--" + boundary + "--" + crlf) ;
		return total ;
	}

	public int writestr(OutputStream os, String str) throws IOException {
		byte[] btmp = str.getBytes(encoding_out) ;
		if (limitsize > 0 && btmp.length > limitsize) {
			int border = limitsize ;
			// check boundary of UTF-8 string
			if ((byte)(btmp[border] & 0xc0) == (byte)0x80) {
				if ((byte)(btmp[border-1] & 0xc0) == (byte)0xc0) border -= 1 ;
				else if ((byte)(btmp[border-2] & 0xc0) == (byte)0xc0) border -= 2 ;
				else if ((byte)(btmp[border-3] & 0xc0) == (byte)0xc0) border -= 3 ;
				else if ((byte)(btmp[border-4] & 0xc0) == (byte)0xc0) border -= 4 ;
				else if ((byte)(btmp[border-5] & 0xc0) == (byte)0xc0) border -= 5 ;
			}
			os.write(btmp, 0, border) ;
			return border ;
		}
		os.write(btmp) ;
		return btmp.length ;
	}

	public String makeBoundary() {
		String ret = "-----------" + System.currentTimeMillis() ;
		return ret ;
	}

	public void checkArgs(String[] args) throws IOException {
		for(int i=0;i<args.length;i++) {
			if ("-rc".equals(args[i])) {
				_RC = args[++i] ;
			}
		}
		p = new SLProperties() ;
		if (!p.isNull("MSG_LENGTH")) {
			try {limitsize = Integer.parseInt(p.getp("MSG_LENGTH")) ;}catch(Exception e){}
		}
	}
}
