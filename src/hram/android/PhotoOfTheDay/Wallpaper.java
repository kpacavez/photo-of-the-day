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
import android.graphics.Rect;
import android.graphics.Paint.Align;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
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
		//Log.d(TAG, "Создание сервиса.");
		
		// настройки
		preferences = getSharedPreferences(Constants.SETTINGS_NAME, 0);
		
		//long lastUpdate = preferences.getLong(Constants.LAST_UPDATE, 0);
		
		ReadFile();
		//Date lastUpdateDate = new Date(lastUpdate);
		//Date currentDate = new Date(System.currentTimeMillis());
		//if(lastUpdateDate.getDate() == currentDate.getDate())
		//{
		//	ReadFile();
		//}
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
		//Log.d(TAG, "Сохранение указателя картинки");
		bm = value;
	}
	
	public void SetCurrentDay(int value)
	{
		currDay = value;
		//Log.d(TAG, String.format("Текущее число %d", currDay));
	}
	
	public int GetCurrentDay()
	{
		return currDay;
	}
	
	public boolean isOnline() 
	{
		//Log.d(TAG, "Вызов isOnline()");
		
		try
		{
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		}
	    catch(Exception e) {
	    	//Log.d(TAG, "Ошибка проверки online");
	    }
		
		return false;
	}
	
	public String GetUrl() throws IOException
    {
		//Log.d(TAG, "Получение URL картинки");
		
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
			
			//String number = ite.child(0).text();
			//if(GetBitmap() != null && new Date().getDate() != Integer.parseInt(number))
			//{
			//	continue;
			//}
			
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
		//Log.d(TAG, "Чтение картинки из файла");
		
		FileInputStream stream = null;
		try 
		{
			stream = openFileInput(Constants.FILE_NAME);
			bm = BitmapFactory.decodeStream(stream);
			
			//Log.d(TAG, "Считана картинка из файла");
			
			currentUrl = preferences.getString(Constants.LAST_URL, "");
			SetCurrentDay(new Date(preferences.getLong(Constants.LAST_UPDATE, 0)).getDate());
				
		} catch (FileNotFoundException e) {
			//Log.d(TAG, "Файл картинки не найден");
		}
		finally {
            if (stream != null) try {
                stream.close();
            } catch (IOException e) {}
        }

	}
	
	public void SaveFile(Bitmap bm, String url)
	{
		//Log.d(TAG, "Сохранение картинки в файл");
		try 
		{
			FileOutputStream fos = openFileOutput(Constants.FILE_NAME, Context.MODE_PRIVATE);
			bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
			fos.close();
			
			// сохранение времени последнего обновления
            SharedPreferences.Editor editor = preferences.edit();
            editor.putLong(Constants.LAST_UPDATE, System.currentTimeMillis());
            editor.putString(Constants.LAST_URL, url);
            editor.commit();
            
            SetCurrentDay(new Date().getDate());

		} catch (IOException e) {
			//Log.d(TAG, "Ошибка сохранения картинки");
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
        	//Log.d(TAG, "Создание MyEngine");
        	
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
        	//Log.d(TAG, "Создание таймера обновлений");
        	
    		timer.scheduleAtFixedRate(new TimerTask() 
    		{
    			@Override
    			public void run() 
    			{
    				//Log.d(TAG, "Сработал таймер обновления");
    				if(wp.GetCurrentDay() != new Date().getDate())
    				{
    					//Log.d(TAG, "Запуск обновления");
    					update();
    				}
    				else
    				{
    					//Log.d(TAG, "Обновление не нужно");
    				}
    			}
    			
    		}, 0, Constants.UPDATE_INTERVAL);
    		
    		//Log.d(TAG, "Таймер обновлений запущен");
    	}
        
        private void CheckOnline()
        {
        	//Log.d(TAG, "Создание таймера проверки соединения");
        	
        	isOnlineChecker.scheduleAtFixedRate(new TimerTask() 
    		{
    			@Override
    			public void run() 
    			{
    				//Log.d(TAG, "Запуск проверки соединения");
    				if(isOnline() == false)
    				{
    					return;
    				}
    				
    				//Log.d(TAG, "Остановка таймера проверки соединения");
    				isOnlineChecker.cancel();
    				
    				//Log.d(TAG, "Запуск обновления");
    				update();
    			}
    			
    		}, 1000, 10000);
        	
        	//Log.d(TAG, "Таймер проверки соединения запущен");
        }
       
        public void update()
        {
        	//Log.d(TAG, "Вызов MyEngine.update()");
        	
        	try 
	    	{
        		if(isOnline() == false)
        		{
        			//Log.d(TAG, "Нет интернет соединения. Запуск проверяльщика");
        			CheckOnline();
        			return;
        		}
        		
            	String url = GetUrl();
            	if(url == null)
            	{
            		//Log.d(TAG, "Ошибка получения URL картинки");
            		CheckOnline();
            		return;
            	}
            	
            	url = url.substring(0,url.length() - 3) + "L";
            	if(currentUrl == url)
            	{
            		//Log.d(TAG, "URL совпадает, еще не обновили");
            		return;
            	}
            	
            	//Log.d(TAG, "Загрузка картинки по адресу: " + url);
            	Bitmap bm = imageDownloader.downloadBitmap(url);
            	if(bm == null)
            	{
            		//Log.d(TAG, "Ошибка загрузки киртинки");
            		CheckOnline();
            		return;
            	}
            	
        		//Log.d(TAG, "Картинка успешно загружена");
        		currentHeight = -1;
        		currentWidth = -1;
        		wp.SetBitmap(bm);
        		SaveFile(bm, url);
        		drawFrame();
	    	} 
		    catch (IOException e) {
		    	//Log.d(TAG, "Ошибка получения URL: " + e.getLocalizedMessage());
			}
		    catch(Exception e) {
		    	//Log.d(TAG, "Ошибка обновления: " + e.getLocalizedMessage());
		    }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) 
        {
            super.onCreate(surfaceHolder);

            //Log.d(TAG, "Вызов MyEngine.onCreate()");
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
        	//Log.d(TAG, "Вызов MyEngine.onVisibilityChanged()");
        	
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
            
            //Log.d(TAG, "Вызов MyEngine.onSurfaceChanged()");
            
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
        	//Log.d(TAG, "Вызов MyEngine.onOffsetsChanged()");
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
        	//Log.d(TAG, "Процедура отрисовки");
        	
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
                		//Log.d(TAG, "Картинки нет рисуем загрузку");
                		double rescaling = (double)mWidth / download.getWidth();
                		int width = (int)(download.getWidth() * rescaling);
                		int offset = (mHeight / 2) - (width / 2);
                		c.drawRect(new Rect(0, 0, mWidth, mHeight), new Paint());
                		c.drawBitmap(Bitmap.createScaledBitmap(download, (int)(download.getWidth() * rescaling), (int)(download.getHeight() * rescaling), true) , 0, offset, null);
                		if(isOnline())
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
                		//Log.d(TAG, "Изменились размеры, изменяем размер");
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
/*
	private class LongOperation extends AsyncTask<String, Integer, String> 
    {
    	private final int count = 30;
    	private final ProgressDialog dialog = new ProgressDialog(Wallpaper.this);
    	
    	@Override
	    protected void onPreExecute() 
	    {
    		dialog.setMessage("Inserting data...");
    		dialog.show();
	    }
    	
	    @Override
	    protected String doInBackground(String... params) 
	    {
	    	try 
	    	{
	    		return GetUrl();
	    	} 
		    catch (IOException e) {
				e.printStackTrace();
			}
		    catch(Exception e) {
		    	e.printStackTrace();
		    }
	    	
	    	return null; 
	    }
	 
	    @Override
		protected void onPostExecute(String result) 
	    {
	    	if (dialog.isShowing()) 
	    	{
	            dialog.dismiss();
	        }
	    	
	    	if(result == null)
	    	{
	    		return;
	    	}
	    	
	    	new DownloadImage().execute(result);
		}
	 
	    @Override
	    protected void onProgressUpdate(Integer... progress) 
	    {
	    	dialog.setProgress(progress[0]);
	    }
    }
*/
/*	
    private class DownloadImage extends AsyncTask<String, Integer, Bitmap> 
    {
    	private final ProgressDialog dialog = new ProgressDialog(Wallpaper.this);
    	
    	@Override
	    protected void onPreExecute() 
	    {
    		dialog.setMessage("Download image...");
    		dialog.show();
	    }
    	
	    @Override
	    protected Bitmap doInBackground(String... params) 
	    {	  
	    	return imageDownloader.downloadBitmap(params[0]);
	    }
	 
	    @Override
		protected void onPostExecute(Bitmap result) 
	    {
	    	if (dialog.isShowing()) 
	    	{
	            dialog.dismiss();
	        }
	    
	    	Wallpaper.this.SetBitmap(result);
		}
	 
	    @Override
	    protected void onProgressUpdate(Integer... progress) 
	    {
	    	dialog.setProgress(progress[0]);
	    }
    }
    */
}
