package hram.android.PhotoOfTheDay;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class Wallpaper extends WallpaperService 
{
	public static final String TAG = "Wallpaper";
	private final Handler mHandler = new Handler();
	private final ImageDownloader imageDownloader = new ImageDownloader();
	private int currDay = -1;
	private Bitmap bm;
	private SharedPreferences preferences;
	private String currentUrl;
	
	@Override
	public void onCreate() 
	{
		//Log.d(TAG, "�������� �������.");
		
		// ���������
		preferences = getSharedPreferences(Constants.SETTINGS_NAME, 0);
		
		ReadFile();
	}

	@Override
	public Engine onCreateEngine() 
	{
		//Log.d(TAG, "WallpaperService started.");
		return new MyEngine(this);
	}
	
	public Bitmap GetBitmap()
	{
		return bm;
	}
	
	public void SetBitmap(Bitmap value)
	{
		//Log.d(TAG, "���������� ��������� ��������");
		bm = value;
	}
	
	public void SetCurrentDay(int value)
	{
		// ��� ������� ����������
		//value -= 1;
		
		Log.d(TAG, String.format("������� �����: %d", value));
		currDay = value;
	}
	
	public int GetCurrentDay()
	{
		return currDay;
	}
	
	public void SetCurrentUrl(String value)
	{
		Log.d(TAG, String.format("������� URL: %s", value));
		currentUrl = value;
	}
	
	public String GetCurrentUrl()
	{
		return currentUrl;
	}
	
	public boolean IsOnline() 
	{
		//Log.d(TAG, "����� isOnline()");
		
		try
		{
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		}
	    catch(Exception e) {
	    	//Log.d(TAG, "������ �������� online");
	    }
		
		return false;
	}
	
	public String GetUrl() throws IOException
    {
		Log.d(TAG, "��������� URL ��������");
		
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
		}
		
		return url;
    }
	
	public void ReadFile()
	{
		Log.d(TAG, "������ �������� �� �����");
		
		FileInputStream stream = null;
		try 
		{
			stream = openFileInput(Constants.FILE_NAME);
			bm = BitmapFactory.decodeStream(stream);
			
			Log.d(TAG, "������� �������� �� �����");
			
			SetCurrentUrl(preferences.getString(Constants.LAST_URL, ""));
			SetCurrentDay(new Date(preferences.getLong(Constants.LAST_UPDATE, 0)).getDate());
				
		} catch (FileNotFoundException e) {
			Log.d(TAG, "���� �������� �� ������");
		}
		finally {
            if (stream != null) try {
                stream.close();
            } catch (IOException e) {}
        }

	}
	
	public void SaveFile(Bitmap bm, String url)
	{
		Log.d(TAG, "���������� �������� � ����");
		try 
		{
			FileOutputStream fos = openFileOutput(Constants.FILE_NAME, Context.MODE_PRIVATE);
			bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
			fos.close();
			
			long now = System.currentTimeMillis(); 
			
			// ���������� ������� ���������� ����������
            SharedPreferences.Editor editor = preferences.edit();
            editor.putLong(Constants.LAST_UPDATE, now);
            editor.putString(Constants.LAST_URL, url);
            editor.commit();
            
            SetCurrentDay(new Date(now).getDate());
            SetCurrentUrl(url);

		} catch (IOException e) {
			Log.d(TAG, "������ ���������� ��������");
		}
	}
	
	class MyEngine extends Engine 
	{
		private final Paint mPaint = new Paint();
        private int mPixels;
        private float mXStep;
        private Timer timer = new Timer();
        private Timer isOnlineChecker = new Timer();
        private int mHeight = -1;
        private int mWidth = -1;
        private Wallpaper wp;
        private int currentHeight = -1;
        private int currentWidth = -1;
        //private Rect mRectFrame;
        private boolean mHorizontal;
        private Bitmap download;

        private final Runnable drawRunner = new Runnable() {
            public void run() {
                drawFrame();
            }
        };
        
        private boolean mVisible;

        MyEngine(Wallpaper service) 
        {
        	//Log.d(TAG, "�������� MyEngine");
        	final Paint paint = mPaint;
            paint.setColor(0xffffffff);
            paint.setTextSize(30);
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.CENTER);
            
        	wp = service;
        	download = BitmapFactory.decodeResource(getResources(),  R.drawable.download);
        	netUpdates();
        }
        
        private void netUpdates()
    	{
        	//Log.d(TAG, "�������� ������� ����������");
        	
    		timer.scheduleAtFixedRate(new TimerTask() 
    		{
    			@Override
    			public void run() 
    			{
    				Log.d(TAG, "�������� ������ ����������");
    				int now = new Date(System.currentTimeMillis()).getDate();
    				if(wp.GetCurrentDay() != now)
    				{
    					Log.d(TAG, "������ ����������");
    					update();
    				}
    				else
    				{
    					Log.d(TAG, String.format("���������� �� �����. ������: %d, �������: %d", now, wp.GetCurrentDay()));
    				}
    			}
    			
    		}, 0, Constants.UPDATE_INTERVAL);
    		
    		//Log.d(TAG, "������ ���������� �������");
    	}
        
        private void CheckOnline()
        {
        	//Log.d(TAG, "�������� ������� �������� ����������");
        	
        	isOnlineChecker.scheduleAtFixedRate(new TimerTask() 
    		{
    			@Override
    			public void run() 
    			{
    				Log.d(TAG, "������ �������� ����������");
    				if(IsOnline() == false)
    				{
    					Log.d(TAG, "�������� ������ ���������");
    					return;
    				}
    				
    				Log.d(TAG, "�������� ������ ��������. ��������� ������� �������� ����������");
    				isOnlineChecker.cancel();
    				
    				Log.d(TAG, "������ ����������");
    				update();
    			}
    			
    		}, 1000, 10000);
        	
        	//Log.d(TAG, "������ �������� ���������� �������");
        }
       
        public void update()
        {
        	Log.d(TAG, "����� MyEngine.update()");
        	
        	try 
	    	{
        		if(IsOnline() == false)
        		{
        			Log.d(TAG, "��� �������� ����������. ������ �������������");
        			CheckOnline();
        			return;
        		}
        		
            	String url = GetUrl();
            	if(url == null)
            	{
            		Log.d(TAG, "������ ��������� URL ��������");
            		CheckOnline();
            		return;
            	}
            	
            	url = url.substring(0,url.length() - 3) + "L";
            	if(GetCurrentUrl() == url)
            	{
            		Log.d(TAG, "URL ���������, ��� �� ��������");
            		return;
            	}
            	
            	Log.d(TAG, "�������� �������� �� ������: " + url);
            	Bitmap bm = imageDownloader.downloadBitmap(url);
            	if(bm == null)
            	{
            		Log.d(TAG, "������ �������� ��������");
            		CheckOnline();
            		return;
            	}
            	
        		Log.d(TAG, "�������� ������� ���������");
        		currentHeight = -1;
        		currentWidth = -1;
        		wp.SetBitmap(bm);
        		SaveFile(bm, url);
        		drawFrame();
	    	} 
		    catch (IOException e) {
		    	Log.d(TAG, "������ ��������� URL: " + e.getLocalizedMessage());
			}
		    catch(Exception e) {
		    	Log.d(TAG, "������ ����������: " + e.getLocalizedMessage());
		    }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) 
        {
            super.onCreate(surfaceHolder);

            //Log.d(TAG, "����� MyEngine.onCreate()");
        }

        @Override
        public void onDestroy() 
        {
            super.onDestroy();
            timer.cancel();
            mHandler.removeCallbacks(drawRunner);
        }

        @Override
        public void onVisibilityChanged(boolean visible) 
        {
        	//Log.d(TAG, "����� MyEngine.onVisibilityChanged()");
        	
            mVisible = visible;
            if (visible) {
                drawFrame();
            } else {
                mHandler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) 
        {
            super.onSurfaceChanged(holder, format, width, height);
            
            //Log.d(TAG, "����� MyEngine.onSurfaceChanged()");
            
            mHeight = height;
            mWidth = width;
            initFrameParams();
            drawFrame();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mVisible = false;
            mHandler.removeCallbacks(drawRunner);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep, int xPixels, int yPixels) 
        {
        	//Log.d(TAG, "����� MyEngine.onOffsetsChanged()");
        	//Log.d(TAG, String.format("xStep: %f, xPixels: %d", xStep, xPixels));
        	
        	mXStep = xStep;
            mPixels = xPixels;
            drawFrame();
        }

        /*
         * Draw one frame of the animation. This method gets called repeatedly
         * by posting a delayed Runnable. You can do any drawing you want in
         * here. This example draws a wireframe cube.
         */
        void drawFrame() 
        {
        	//Log.d(TAG, "��������� ���������");
        	
            final SurfaceHolder holder = getSurfaceHolder();

            Canvas c = null;
            try 
            {
                c = holder.lockCanvas();
                Bitmap bm = wp.GetBitmap();
                if (c != null) 
                {
                	if(bm == null)
                	{
                		//Log.d(TAG, "�������� ��� ������ ��������");
                		double rescaling = (double)mWidth / download.getWidth();
                		int width = (int)(download.getWidth() * rescaling);
                		int offset = (mHeight / 2) - (width / 2);
                		c.drawRect(new Rect(0, 0, mWidth, mHeight), new Paint());
                		c.drawBitmap(Bitmap.createScaledBitmap(download, (int)(download.getWidth() * rescaling), (int)(download.getHeight() * rescaling), true) , 0, offset, null);
                		if(IsOnline())
                		{
                			c.drawText(getText(R.string.download).toString(), mWidth / 2, 100, mPaint);
                		}
                		else
                		{
                			c.drawText(getText(R.string.error).toString(), mWidth / 2, 100, mPaint);
                			c.drawText(getText(R.string.isOffline).toString(), mWidth / 2, 150, mPaint);
                		}
                		return;
                	}
                	
                	if(mHeight != currentHeight || mWidth != currentWidth)
                	{
                		//Log.d(TAG, "���������� �������, �������� ������");
                		double rescaling = (double)mHeight / bm.getHeight();
                		if(mHorizontal)
                		{
                			rescaling = (double)mWidth / bm.getWidth();
                			rescaling *=1.5;
                		}
                		

                		bm = Bitmap.createScaledBitmap(bm, (int)(bm.getWidth() * rescaling), (int)(bm.getHeight() * rescaling), true);
                		wp.SetBitmap(bm);
                		currentHeight = mHeight;
                		currentWidth = mWidth;
                	}
                	
                	
                	if(isPreview() == false)
                	{
	                	float step1 = mWidth * mXStep;
	                	float step2 = (bm.getWidth() - mWidth) * mXStep;
	                	float d = step2 / step1;
	                	c.translate((float)mPixels * d, 0f);
                	}
                	
                	if(mHorizontal)
                		c.drawBitmap(bm, 0, -currentHeight / 3, null);
                	else
                		c.drawBitmap(bm, 0, 0, null);
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }

            // Reschedule the next redraw
            mHandler.removeCallbacks(drawRunner);
            if (mVisible) 
            {
                //mHandler.postDelayed(drawRunner, 1000 / 25);
            }
        }
        
        void initFrameParams()
        {
        	DisplayMetrics metrics = new DisplayMetrics();
        	Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        	display.getMetrics(metrics);

        	//mRectFrame = new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);


        	int rotation = display.getOrientation();
        	if(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
        	{
        	    mHorizontal = false;
        	}
        	else
        	{
        	    mHorizontal = true;
        	}
        }
	}
}
