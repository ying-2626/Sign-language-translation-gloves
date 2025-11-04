package com.example.yyma.fingerinput;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by heiba on 2017/9/14.
 */
public class WordBank {
    private String words = null;
    private BufferedReader bfr = null;
    private String filename = null;
    public WordBank(String filename){
        this.filename = filename;
    }
    public void load (){
        StringBuffer sb =  new StringBuffer("");
        try {
            FileReader reader = new FileReader(filename);
            bfr = new BufferedReader(reader);
            String str;
            while((str = bfr.readLine())!=null){
                sb.append(str);
            }
            words = sb.toString();
            bfr.close();
            reader.close();
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    public String getWords(){
        return this.words;
    }
}
