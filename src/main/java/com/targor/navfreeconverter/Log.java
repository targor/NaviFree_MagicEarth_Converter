package com.targor.navfreeconverter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Log 
{
    
    public enum Logtype{info,error,warning,verbose}
    public enum Logverbosity{all,error_only,error_and_warnings,verbose}
    
    public static Logverbosity logVerbosity=Logverbosity.all;
    
    public static void error(String text,Exception e)
    {
        StringWriter sw = new StringWriter();
        try{e.printStackTrace(new PrintWriter(sw));}catch(Exception exc2){}
        String exceptionAsString = sw.toString();
        l(text+" stacktrace:"+exceptionAsString,Logtype.error);
    }
    
    public static void error(String text) {l(text,Logtype.error);}
    public static void info(String text) {l(text,Logtype.info);}
    public static void verbose(String text) {l(text,Logtype.verbose);}
    public static void warning(String text) {l(text,Logtype.warning);}
    
    public static void l(String text,Logtype logtype)
    {
        try
        {
            java.util.Date date = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String strDate = dateFormat.format(date);

            System.out.println(strDate+":"+text);
        }
        catch(Exception exc)
        {
            System.out.println("Could not write to logfile.");
        }
    }
}
