package hram.android.PhotoOfTheDay.Parsers;

import java.io.IOException;

public abstract class BaseParser 
{
	public static final String TAG = "Wallpaper";
	
	public abstract String GetUrl() throws IOException;
}
