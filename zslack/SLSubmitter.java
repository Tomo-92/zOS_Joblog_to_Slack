package zslack ;

import com.ibm.jzos.* ;
import java.io.* ;
import java.util.* ;

public class SLSubmitter {
	SLProperties p = null ;

	public static void main(String[] args) throws Exception {
		(new SLSubmitter()).main2(args) ;
	}
	public void main2(String[] args) throws Exception {
		checkArgs(args) ;
		ArrayList list = genJCL() ;
		submit(list) ;
	}

	public ArrayList genJCL() {
		ArrayList<String> list = new ArrayList<String>() ;
		String _JP = p.getp("JCLPARAM") ;
		String _JP2 = p.getp("JCLPARAM2") ;
		String _JP3 = p.getp("JCLPARAM3") ;

		if (_JP2 == null && _JP3 != null) { _JP2 = _JP3 ; _JP3 = null ;}
		if (_JP2 != null && !_JP.endsWith(",")) _JP = _JP + "," ;
		list.add("//"+pad8(p.getp("BATCHNAME"))+" JOB '"+p.getp("BATCHNAME")+"',"+_JP) ;
		if (_JP2 != null) {
			if (_JP3 != null && !_JP2.endsWith(",")) _JP2 = _JP2 + "," ;
			list.add("//         "+_JP2) ;
			if (_JP3 != null) {
				list.add("//         "+_JP3) ;
			}
		}

		list.add("//TSOBATCH EXEC PGM=SDSF") ;
		for (int i=0;i<p._logs.size();i++) {
			list.add("//WORK"+(i+1)+"    DD DISP=(,PASS),DSN=&&WORK"+(i+1)+",RECFM=FB,LRECL=133") ;
		}
		list.add("//ISFOUT   DD SYSOUT=*") ;
		list.add("//ISFIN    DD *") ;
		list.add("ST "+p.getp("JOBNAME")) ;
		list.add("SELECT "+p.getp("JOBNAME")+"  "+p.getp("JOBID")) ;
		list.add("FIND "+p.getp("JOBNAME")) ;
		list.add("++?") ;

		for (int i=0;i<p._logs.size();i++) {
			String[] _s = ((String)p._logs.get(i)).split("[ /]") ;
			if (_s.length > 1) {
				list.add("FILTER STEPNAME " + _s[1]) ;
				p._logs.set(i, _s[0]+"-"+_s[1]) ; //overwriten
			}
			list.add("UP") ;
			list.add("FIND " +_s[0]) ;
			list.add("++S") ;
			list.add("PT FILE WORK"+(i+1)) ;
			list.add("PT") ;
			list.add("PT CLOSE") ;
			list.add("END") ;
			list.add("FILTER STEPNAME *") ;
		}
		list.add("//*") ;

		// Java Program
		list.add("//JAVAJVM  EXEC PGM=JVMLDM86,PARM='zslack.SLSendMainB'") ;
		list.add("//STEPLIB  DD DISP=SHR,DSN="+p.getp("JZOSLIB")) ;
		for (int i=0;i<p._logs.size();i++) {
			list.add("//WORK"+(i+1)+"    DD DISP=(OLD,DELETE),DSN=&&WORK"+(i+1)+",RECFM=FB,LRECL=133") ;
		}
		list.add("//SYSPRINT DD SYSOUT=* < System stdout") ;
		list.add("//SYSOUT   DD SYSOUT=* < System stderr") ;
		list.add("//STDOUT   DD SYSOUT=* < Java System.out") ;
		list.add("//STDERR   DD SYSOUT=* < Java System.err") ;
		list.add("//STDENV   DD *") ;
		p.envlist.add("MYAPP") ;
		for(String k: p.envlist){
			if (k.startsWith("LOGLIST") || "LOGCOUNT".equals(k)) continue ;
			String v = p.getp(k) ;
			if (v.indexOf(' ') == -1) list.add("export "+k+"="+v) ;
			else list.add("export "+k+"=\""+v+"\"") ;
		}
		list.add("export CLASSPATH=$MYAPP:$CLASSPATH") ;
		list.add("PATH=$JAVA_HOME/bin:$PATH") ;
		list.add("export LIBPATH=$JAVA_HOME/bin/j9vm:$LIBPATH") ;
		list.add("export LOGCOUNT="+p._logs.size()) ;
		for(int i=0;i<p._logs.size();i++) {
			list.add("export LOGLIST"+(i+1)+"="+p._logs.get(i)) ;
		}
		list.add("//*") ;
		list.add("//") ;
		return list ;
	}

	public static String pad8(String str) {
		if (str.length() == 8) return str ;
		else if (str.length() < 8) return (str + "        ".substring(str.length())) ;
		else return str.substring(0, 8) ;
	}

	public void submit(ArrayList list) throws Exception {
		MvsJobSubmitter jobsubm = new MvsJobSubmitter() ;
		for(int i=0;i<list.size();i++) {
			jobsubm.write((String)list.get(i)) ;
		}
		jobsubm.close() ;
	}

	public static void printerr(String str) {
		printerrmsg(str+" is missing..") ;
	}
	public static void printerrmsg(String str) {
		System.err.println("SLSubmitter: "+str) ;
		System.exit(1) ;
	}

	public void checkArgs(String[] args) throws IOException {
		p = new SLProperties() ;
		if (p.isNull("JOBNAME")) { p.setProperty("JOBNAME", ZUtil.getCurrentJobname());}
		if (p.isNull("JOBID")) p.setProperty("JOBID", ZUtil.getCurrentJobId()) ;
		if (p._logs.size() == 0) printerrmsg("LOGLIST IS NULL...") ;
		if (p.isNull("JZOSLIB")) printerrmsg("JZOSLIB is required.") ;
	}
}
