package com.targor.navfreeconverter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


public class Helper {
    
    
    public static void deleteFile(String filename)
    {
        deleteFile(new File(filename));
    }
    
    public static void deleteFile(File filename)
    {
        try{filename.delete();}
        catch(Exception exc)
        {
            Log.error("Could not delete file: "+filename.getAbsolutePath(), exc);
        }
    }
    
    public static String readFile(String filename)
    {
        StringBuilder sb = new StringBuilder();
        try
        {
            File file = new File(filename); 

            BufferedReader br = new BufferedReader(new FileReader(file)); 
            
            String st; 
            while ((st = br.readLine()) != null) 
            {
                sb.append(st);
                sb.append("\n");
            }
            br.close();
        } 
        catch(Exception exc){}
        return sb.toString();
    }
    
    public static void writeToFile(String text,String filename)
    {
        writeToFile(text,filename,false);
    }
    
    public static void writeToFile(String text,String filename,boolean append)
    {
        if (append)
        {
            File f = new File(filename);
            try {
                if (!f.exists()){f.createNewFile();}
                Files.write(Paths.get(filename), ("\n"+text).getBytes(), StandardOpenOption.APPEND);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        else
        {
            try{
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            bw.write(text);
            bw.flush();
            bw.close();
            }catch(Exception exc)
            {
                exc.printStackTrace();
            }
        }
    }
  
   
}
