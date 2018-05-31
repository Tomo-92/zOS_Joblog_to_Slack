package zslack ;

import java.io.IOException ;
import java.io.InputStreamReader ;
import java.io.OutputStream ;
import java.io.ByteArrayOutputStream ;
import java.io.BufferedReader ;
import java.net.URL ;
import java.net.HttpURLConnection ;
import java.util.List ;
import java.util.zip.ZipOutputStream ;
import java.util.zip.ZipEntry ;
import com.ibm.jzos.ZUtil ;
import com.ibm.zos.sdsf.core.ISFRequestSettings ;
import com.ibm.zos.sdsf.core.ISFStatusRunner ;
import com.ibm.zos.sdsf.core.ISFStatus ;
import com.ibm.zos.sdsf.core.ISFRequestResults ;
import com.ibm.zos.sdsf.core.ISFJobDataSet ;


public class SLSendMainC {
	static final String encoding_in = "Cp939" ;
	static final String encoding_out = "UTF-8" ;
	static final String CRLF = "\r\n" ;
	static final String CR = "\n" ;
	static final String DQ = "\"" ;

	int limitsize = 0 ;
	String _RC = null ;
	SLProperties p = null ;

	public static void main(String[] args) throws Exception {
		(new SLSendMainC()).main2(args) ;
	}

	public String getFilter() {
		if (!p.isNull("SLACK_WEBHOOK")&&!p.isNull("SLACK_WEBHOOK_REGEX")){
			return ".*"+p.getp("SLACK_WEBHOOK_REGEX")+".*" ;
		}else {
			return null ;
		}
	}

	public void main2(String[] args) throws Exception {
		checkArgs(args) ;
		ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
		ZipOutputStream zos = new ZipOutputStream(baos) ;
		String _FILTER = getFilter() ;
		StringBuilder _filter_res = (_FILTER == null ? null : new StringBuilder(2014)) ;

		ISFRequestSettings settings = new ISFRequestSettings();
		settings.addISFPrefix("**");
		settings.addISFOwner("*");
		settings.addISFFilter("jname eq " + p.getp("JOBNAME") + " jobid eq " + p.getp("JOBID")) ;

		ISFStatusRunner runner = new ISFStatusRunner(settings);
		List<ISFStatus> objs = runner.exec() ;
		ISFRequestResults reqres = runner.getRequestResults() ;

		if (objs.size() == 1) {
			ISFStatus stat1 = objs.get(0) ;
			List<ISFJobDataSet> list1 = stat1.getJobDataSets() ;
			for (ISFJobDataSet ds : list1) {
				if (p._logs.contains(ds.getDDName())) {
					ds.browse() ;
					List<String> response = reqres.getResponseList() ;
					zos.putNextEntry(new ZipEntry(ds.getDDName()+".txt")) ;
					boolean flag = false ;
					for(String str : response) {
						int i = str.length() -1 ;
						for( ;i>0 && str.charAt(i) == ' ';i--) ;
						if (i < str.length()-1) str = str.substring(0, i+1) ;
						zos.write((str+CR).getBytes(encoding_out)) ;
						if (_FILTER!=null&&str.matches(_FILTER)) {
							if (!flag) { flag = true ; _filter_res.append("```") ;}
							_filter_res.append(str).append(CR) ;
						}
					}
					if (flag) { flag = false ; _filter_res.append("```").append(CR) ;}
				}else {
					System.out.println("=== not target : " + ds.getDDName()) ;
				}
			}
			zos.close() ;
			send_fileupload(baos.toByteArray()) ;
			if (_filter_res != null) send_webhook(_filter_res.toString()) ;
		}else {
			System.out.println("objsize = " + objs.size()) ;
		}

	}

	public void send_webhook(String message) throws IOException {
		if ("".equals(message)) {System.out.println("message is empty"); return ;}

		byte[] payload = webhook_payload(message) ;
		URL _url = new URL(p.getp("SLACK_WEBHOOK")) ;
		HttpURLConnection con = (HttpURLConnection)_url.openConnection() ;
		con.setRequestProperty("Content-Type", "application/json; charset="+encoding_out) ;
		con.setDoInput(true) ;
		con.setDoOutput(true) ;
		con.setRequestMethod("POST") ;
		con.setFixedLengthStreamingMode(payload.length) ;
		con.connect() ;
		OutputStream os = con.getOutputStream() ;
		os.write(payload) ;

		print_response("webhook", con) ;
		con.disconnect() ;
	}

	public byte[] webhook_payload(String message) throws IOException {
		StringBuilder sbuf = new StringBuilder(256) ;
		sbuf.append("{") ;
		sbuf.append(quote("text")).append(":").append(quote(message)) ;
		if (!p.isNull("SLACK_WEBHOOK_CHANNEL")) sbuf.append(",").append(quote("channel")).append(":").append(quote(p.getp("SLACK_WEBHOOK_CHANNEL"))) ;
		if (!p.isNull("SLACK_WEBHOOK_USERNAME"))sbuf.append(",").append(quote("username")).append(":").append(quote(p.getp("SLACK_WEBHOOK_USERNAME"))) ;
		sbuf.append("}") ;
		return sbuf.toString().getBytes(encoding_out) ;
	}

	String quote(String s) {
		return DQ+s+DQ ;
	}

	public void send_fileupload(byte[] buf) throws IOException {
		String token = p.getp("SLACK_TOKEN") ;
		String channels = p.getp("SLACK_CHANNEL") ;
		if (channels == null) {System.err.println("CHANNEL is undefined..") ; System.exit(0); }
		String filename = "archives.zip" ;
		String title = p.getp("TITLE") ;
		if (title == null) title = "Message from z/OS" ;
		String comment = p.getp("COMMENT") ;
		if (comment == null) comment = "" ;
		String url = p.getp("SLACK_FILE_URL") ;

		String[] prop_name = new String[]{"token", "filename", "filetype", "channels", "title", "initial_comment"} ;
		String[] prop_body = new String[]{token, filename, "zip", channels, title, comment} ;

		senderC(prop_name, prop_body, url, buf) ;
	}

	public void senderC(String[] prop_name, String[] prop_body, String url, byte[] buf) throws IOException {
		String boundary = makeBoundary() ;
		byte[] body = create_multi(boundary, prop_name, prop_body, buf) ;

		URL _url = new URL(url) ;
		HttpURLConnection con = (HttpURLConnection)_url.openConnection() ;
		con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary) ;
		con.setDoInput(true) ;
		con.setDoOutput(true) ;
		con.setRequestMethod("POST") ;

		con.setFixedLengthStreamingMode(body.length) ;
		con.connect() ;
		OutputStream os = con.getOutputStream() ;
		os.write(body) ;

		print_response("file.upload", con) ;
		con.disconnect() ;
	}

	public byte[] create_multi(String boundary, String[] keys, String[] values, byte[] buf) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
		createbody_text(baos, keys, values, boundary) ;
		createbody_zip(baos, buf, boundary) ;
		return baos.toByteArray() ;
	}

	public int createbody_text(OutputStream os, String[] prop_name, String[] prop_body, String boundary) throws IOException {
		int total = 0 ;
		for(int i=0;i<prop_name.length;i++) {
			total += writestr(os, "--" + boundary + CRLF) ;
			total += writestr(os, "Content-Disposition: form-data; name=\""+prop_name[i]+"\""+CRLF+CRLF) ;
			byte[] btmp = prop_body[i].getBytes(encoding_out) ;
			os.write(btmp) ;
			total += btmp.length ;
			total += writestr(os, CRLF) ;
		}
		return total ;
	}

	public int createbody_zip(OutputStream os, byte[] buf, String boundary) throws IOException {
		int total = 0 ;
		total += writestr(os, "--" + boundary + CRLF) ;
		total += writestr(os, "Content-Disposition: form-data; name=\"file\"; filename=\"archives.zip\""+CRLF) ;
		total += writestr(os, "Content-Type: application/zip"+CRLF+CRLF) ;

		os.write(buf) ;
		total += buf.length ;
		total += writestr(os, CRLF + "--" + boundary + "--" + CRLF) ;
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

	public void print_response(String header, HttpURLConnection con) throws IOException {
		int rc = con.getResponseCode() ;
		String rs = con.getResponseMessage() ;
		System.out.println("---- " + header + " ----") ;
		System.out.println(">>> RESULT: RC="+rc+": " +rs) ;
		BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), encoding_out)) ;
		String line = null ;
		while((line = reader.readLine()) != null) {
			System.out.println(line) ;
		}
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
		if (p.isNull("JOBNAME")) { p.setProperty("JOBNAME", ZUtil.getCurrentJobname());}
		if (p.isNull("JOBID")) p.setProperty("JOBID", ZUtil.getCurrentJobId()) ;
		if (p._logs.size() == 0) printerrmsg("LOGLIST IS NULL...") ;
	}

	public void printerr(String str) {
		printerrmsg(str+" is missing..") ;
	}

	public void printerrmsg(String str) {
		System.err.println("SLSubmitter: "+str) ;
		System.exit(1) ;
	}
}
