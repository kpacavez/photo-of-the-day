package hram.android.PhotoOfTheDay.Parsers;

import java.io.IOException;
import java.util.Date;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.util.Log;

public class Flickr extends BaseParser 
{	
	@Override
	public String GetUrl() throws IOException 
	{
		String str;
		String imageUrl = null;
		long now = System.currentTimeMillis(); 
		Date date = new Date(now);
		
		String url = String.format("http://www.flickr.com/explore/interesting/%d/%02d/%02d/", date.getYear() + 1900, date.getMonth() + 1, date.getDate());
		
		Document doc = Jsoup.connect(url).get();
		
		Element table = doc.select("table[class=DayView]").first();
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
			imageUrl = src.attr("src");
			imageUrl = imageUrl.substring(0,imageUrl.length() - 5) + "z.jpg";
			break;
		}
		
		return imageUrl;
	}

}
