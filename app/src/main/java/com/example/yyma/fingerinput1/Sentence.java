package com.example.yyma.fingerinput1;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by heiba on 2017/8/30.
 */
public class Sentence {
    private String stc;
    // 拼音之间以 ‘|’ 分隔 例如 dian4|zi3|gong1|cheng2
    private String characters[];
    private StringBuffer ret = new StringBuffer("");
    private String words = null;
    private final int maxLen = 6;

    public void redim (String sentence){
        ret.setLength(0);
        this.stc = sentence;
        characters = stc.split("\\u007C");
    }

    public Sentence(String sentence, String words) {
        this.stc = sentence;
        this.words = words;
        characters = stc.split("\\u007C");
    }

    public void transfer(){
        int len = characters.length;
        int end = len;
        System.out.println(words);
        System.out.println(this.stc);
        Boolean flag=false;
        int max;
        if(len>maxLen){
            max = maxLen;
        }else{
            max = len;
        }
        if (len>=max){
            StringBuffer stringBuffer = new StringBuffer("");
            for (int i=len-max;end>0;){

                /**
                 * 取最后maxLen个字的拼音
                 */
                stringBuffer.setLength(0);
                for(int j=i;j<end-1;j++){
                    stringBuffer.append(characters[j]+"\\|");
                }
                stringBuffer.append(characters[end-1]+" ");

                /**
                 * 查找这个词
                 */
                String regEx = "([\u4e00-\u9fa5]+ ("+stringBuffer+"))";
                //System.out.println("String buffer: "+regEx);
                Pattern pattern = Pattern.compile(regEx);
                //System.out.println("regex: "+pattern.toString());
                //System.out.println(this.words);
                Matcher matcher = pattern.matcher(this.words);
                if(matcher.find()){
                    flag = true;
                    String[] temp = matcher.group(1).split(" ");
                    //System.out.println(matcher.group(1));
                    ret.insert(0,temp[0]);
                    //System.out.println(ret);
                }
                //System.out.println(flag);

                if(flag) {
                    /**
                     * 找到
                     * 起始位置i前移maxLen 尾end前移maxLen
                     */
                    if (i > maxLen) {
                        i = i - maxLen;
                        end = end - maxLen;
                    } else {
                        end = i;
                        i = 0;
                    }
                    flag = false;
                }else{
                    /**
                     * 没找到
                     * 查找子串
                     */
                    Boolean tflag = false;
                    int k;
                    StringBuffer tsb = new StringBuffer("");
                    for(k = end-i;k>0;k--){
                        tsb.setLength(0);
                        for (int p=end-k;p<end-1;p++){
                            tsb.append(characters[p]+"\\|");
                        }
                        tsb.append(characters[end-1]+" ");
                        /**
                         * 查找
                         * 如果找到 tflag=true
                         */
                        regEx = "([\u4e00-\u9fa5]+ ("+tsb+"))";
                        pattern = Pattern.compile(regEx);
                        //System.out.println(regEx);
                        matcher = pattern.matcher(this.words);
                        if(matcher.find()){
                            tflag = true;
                            String[] tmp = matcher.group(1).split(" ");
                            ret.insert(0,tmp[0]);
                            //System.out.println(ret);
                        }

                        if(tflag==true){
                            break;
                        }else if(k==1) {
                            ret.insert(0, tsb);
                            //System.out.println(ret);
                            break;
                        }
                    }
                    //System.out.println("i:" + i +"  k :"+ k+" end:"+end);
                    if(i>k){
                        i=i-k;
                        end=end-k;
                    }else{
                        end=end-k;
                        i=0;
                    }
                }
            }
        }
    }

    public String getRet(){
        return this.ret.toString();
    }

}
