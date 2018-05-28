package zslack ;

import java.io.BufferedReader ;
import java.io.IOException ;
import java.io.InputStreamReader ;
import java.io.OutputStream ;
import java.net.URL ;
import java.net.HttpURLConnection ;

public class SLSimplesend {
	String encoding_in = "CP939" ;
	String encoding_out = "UTF-8" ;
	public static void main(String[] args) throws Exception {
		System.out.println("ret = " + send(args[0])) ;
	}

	public static String send(String message) throws IOException {
		SLProperties p = new SLProperties() ;
		return (new SLSimplesend()).sendmain(p.getp("SLACK_WEBHOOK"), message, p.getp("SLACK_WEBHOOK_CHANNEL"), p.getp("SLACK_WEBHOOK_USERNAME")) ;
	}

	public String sendmain(String url, String message, String channel, String username) throws IOException {
		SLProperties p = new SLProperties() ;
		URL _url = new URL(url) ;
		HttpURLConnection con = null ;

		StringBuilder sbuf = new StringBuilder() ;
		sbuf.append(quote("text")).append(":").append(quote(message)) ;
		if (!(channel==null||"".equals(channel))) sbuf.append(",").append(quote("channel")).append(":").append(quote(channel)) ;
		if (!(username==null||"".equals(username))) sbuf.append(",").append(quote("username")).append(":").append(quote(username)) ;
		byte[] payload = ("{" + sbuf.toString() + "}").getBytes("UTF-8") ;
		sbuf = new StringBuilder() ;

		con = (HttpURLConnection)_url.openConnection() ;
		con.setRequestProperty("Content-Type", "application/json; charset=UTF-8") ;
		con.setDoInput(true) ;
		con.setDoOutput(true) ;
		con.setRequestMethod("POST") ;
		con.setFixedLengthStreamingMode(payload.length) ;
		con.connect() ;
		OutputStream os = con.getOutputStream() ;
		os.write(payload) ;

		sbuf.append(con.getResponseCode()).append(":").append(con.getResponseMessage()) ;
		char[] cbuf = new char[256] ;
		int csize = 0 ;
		BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), encoding_out)) ;
		while((csize = reader.read(cbuf)) > 0) {
			sbuf.append(cbuf, 0, csize) ;
		}
		con.disconnect() ;
		return sbuf.toString() ;
	}
	public String quote(String s) {
		return "\""+s+"\"" ;
	}
}
