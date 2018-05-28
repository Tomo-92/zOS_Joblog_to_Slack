package zslack ;

import java.io.IOException ;
import java.io.InputStream ;
import java.net.URL ;
import java.util.ArrayList ;
import java.util.HashSet ;
import java.util.Properties ;

public class SLProperties extends Properties {
	public ArrayList<String> _logs = new ArrayList<String>() ;
	public HashSet<String> envlist = new HashSet<String>() ;
	String filename = "zslack.properties" ;

	public SLProperties() throws IOException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader() ;
		URL resurl = null ;
		String proploc = null ;

		if (cl == null) resurl = ClassLoader.getSystemResource(filename) ;
		else resurl = cl.getResource(filename) ;

		if (resurl != null) {
			InputStream resis = resurl.openStream() ;
			this.load(resis) ;

			String envkeys = getProperty("ENVIRON_KEYS") ;
			if (envkeys!=null) {
				String _tmp ;
				for (String key: envkeys.split("[ ,]")) {
					if ((_tmp = System.getenv(key)) != null) {
						setProperty(key, _tmp) ;
						envlist.add(key) ;
					}
				}
			}

			if (isNull("MYAPP")) {
				int idx ;
				String res = resurl.toString() ;
				if ((idx = res.lastIndexOf("!")) == -1) idx = res.lastIndexOf("/") ;
				setProperty("MYAPP", res.substring(res.lastIndexOf(":")+1, idx)) ;
			}

			if (!isNull("LOGLIST")) {
				for(String s: getProperty("LOGLIST").split("[ ,]")) _logs.add(s) ;
			}
			if (!isNull("LOGCOUNT")) {
				int c = Integer.parseInt(getProperty("LOGCOUNT")) ;
				for (int j=1;j<=c;j++) {
					for(String s: getProperty("LOGLIST"+j).split("[ ,]")) _logs.add(s) ;
				}
			}
		}
	}
	public boolean isNull(String s) {
		String ss=getProperty(s) ;
		return (ss == null || "".equals(ss)) ;
	}
	public String getp(String k) {
		String r = getProperty(k) ;
		if ("".equals(r)) r = null ;
		return r ;
	}
}
