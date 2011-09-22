package hram.android.PhotoOfTheDay.Parsers;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.util.Log;

public class Yandex extends BaseParser 
{	
	@Override
	public String GetUrl() throws IOException
	{
    	String url = null;
        String str;
        
        Document doc = Jsoup.connect("http://fotki.yandex.ru/calendar").get();
			
		Element table = doc.select("table[class=photos]").first();
		for(Element ite: table.select("td"))
		{
			str = ite.toString();
			if(str == null || ite.childNodes().size() == 0)
			{
				continue;
			}
			
			Element href = ite.select("a[href]").first();
			Element src = ite.select("img[src]").first();
			if(href == null || src == null)
			{
				continue;
			}
			
			str = href.attr("href");
			url = src.attr("src");
			url = url.substring(0,url.length() - 3) + "L";
		}
		
		return url;
	}

}
