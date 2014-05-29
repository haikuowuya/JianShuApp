package jianshu.io.app.model;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import net.tsz.afinal.FinalHttp;

import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.HttpContext;

import java.util.ArrayList;
import java.util.List;

import jianshu.io.app.util.WebViewCookieParser;

/**
 * Created by Administrator on 2014/4/12.
 * session_id 每次登陆都会变化
 * user_token 对用户来说是唯一不变的
 */
public class JianshuSession {

  static final String DOMAIN = "jianshu.io";

  private static JianshuSession sInstance;

  public static synchronized JianshuSession init(Context context) {
    if (sInstance == null) {
      sInstance = new JianshuSession(context);
    }
    return sInstance;
  }

  public static JianshuSession getsInstance() {
    return sInstance;
  }

  private Context context;
  private JianshuSessionState mSessionState;
  private FinalHttp mFinalHttpForHtml;
  private FinalHttp mFinalHttpForJavaScript;
  private String cookieStr;
  private CookieManager mCookieManager;
  private List<BasicClientCookie> mCookieList;
  private List<JianshuSessionListener> listeners = new ArrayList<JianshuSessionListener>();
  private String mSession;

  private JianshuSession(Context context) {
    this.context = context;

    mFinalHttpForJavaScript = new FinalHttp();
    mFinalHttpForJavaScript.addHeader("Accept", "*/*;q=0.5, text/javascript, application/javascript, application/ecmascript, application/x-ecmascript");
    mFinalHttpForJavaScript.addHeader("Accept-Language", "zh-CN,zh;q=0.8");

    mFinalHttpForHtml = new FinalHttp();
    mFinalHttpForHtml.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
    mFinalHttpForHtml.addHeader("Accept-Language", "zh-CN,zh;q=0.8");

    CookieSyncManager.createInstance(context);
    mCookieManager = CookieManager.getInstance();

    validate();
  }

  public synchronized void validate() {
    mSession = null;
    this.cookieStr = mCookieManager.getCookie(DOMAIN);
    if(this.cookieStr == null) {
      setState(new LogoutState());
      return;
    }
    mCookieList = new WebViewCookieParser().parse(this.cookieStr, DOMAIN);
    for (BasicClientCookie cookie : mCookieList) {
          if (cookie.getName().trim().equals("remember_user_token")) {
            mSession = cookie.getValue();
          }
      }
    if (mSession != null) {
      setState(new LoginState());
    } else {
      setState(new LogoutState());
    }
  }

  public void addListener(JianshuSessionListener listener) {
    synchronized (this.listeners) {
      this.listeners.add(listener);
    }
  }

  public void removeListener(JianshuSessionListener listener) {
    synchronized (this.listeners) {
      this.listeners.remove(listener);
    }
  }

  private void onLogin() {
    synchronized (listeners) {
      for(JianshuSessionListener listener : listeners) {
        listener.onLogin();
      }
    }
  }

  private void onLogout() {
    synchronized (listeners) {
      for(JianshuSessionListener listener : listeners) {
        listener.onLogout();
      }
    }
  }

  public synchronized boolean isUserLogin() {
    return getState() instanceof LoginState;
  }

  private synchronized void setState(JianshuSessionState newState) {
    //虽然登陆有可能是换了用户，但是退出的状态就不会是叠加的
    if (mSessionState instanceof LogoutState && newState instanceof LogoutState) {
      return;
    }
    mSessionState = newState;
    if (this.listeners != null) {
      if (newState instanceof LoginState) {
        onLogin();
      } else {
        onLogout();
      }
    }
  }

  public synchronized JianshuSessionState getState() {
    return mSessionState;
  }

  public synchronized void notifyUserLogin() {
    mSessionState.notifyUserLogin(this);
  }

  public synchronized void notifyUserLogout() {
    mSessionState.notifyUserLogout(this);
  }

  public synchronized Object getSync(String url, boolean isHtml) {
    return mSessionState.getSync(this, url, isHtml);
  }

  public synchronized Object postSync(String url, boolean isHtml) {
    return mSessionState.postSync(this, url, isHtml);
  }

  public String getSession() {
    return mSession;
  }


  abstract class JianshuSessionState {

    public abstract void notifyUserLogin(JianshuSession session);

    public abstract void notifyUserLogout(JianshuSession session);

    public abstract Object getSync(JianshuSession session, String url, boolean isHtml);

    public abstract Object postSync(JianshuSession session, String url, boolean isHtml);

  }

  public class LoginState extends JianshuSessionState {

    public LoginState() {
      BasicCookieStore cookieStore = new BasicCookieStore();
      for (BasicClientCookie cookie : mCookieList) {
        cookieStore.addCookie(cookie);
      }
      HttpContext httpContext;
      httpContext = mFinalHttpForJavaScript.getHttpContext();
      httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
      httpContext = mFinalHttpForHtml.getHttpContext();
      httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
    }

    @Override
    public void notifyUserLogin(JianshuSession session) {
      String cookieStr = mCookieManager.getCookie(DOMAIN);
      //如果浏览器cookie发生变化，那么这是一个新的登陆
      if (!cookieStr.equals(JianshuSession.this.cookieStr)) {
        validate();
      }
    }

    @Override
    public void notifyUserLogout(JianshuSession session) {
      session.setState(new LogoutState());
    }

    @Override
    public Object getSync(JianshuSession session, String url, boolean isHtml) {
      FinalHttp finalHttp = isHtml ? mFinalHttpForHtml : mFinalHttpForJavaScript;
      return finalHttp.getSync(url);
    }

    @Override
    public Object postSync(JianshuSession session, String url, boolean isHtml) {
      FinalHttp finalHttp = isHtml ? mFinalHttpForHtml : mFinalHttpForJavaScript;
      return finalHttp.postSync(url);
    }

  }

  public class LogoutState extends JianshuSessionState {

    @Override
    public void notifyUserLogin(JianshuSession session) {
      validate();
    }

    @Override
    public void notifyUserLogout(JianshuSession session) {
    }


    @Override
    public Object getSync(JianshuSession session, String url, boolean isHtml) {
      FinalHttp finalHttp = isHtml ? mFinalHttpForHtml : mFinalHttpForJavaScript;
      return finalHttp.getSync(url);
    }

    @Override
    public Object postSync(JianshuSession session, String url, boolean isHtml) {
      FinalHttp finalHttp = isHtml ? mFinalHttpForHtml : mFinalHttpForJavaScript;
      return finalHttp.postSync(url);
    }

  }

  public interface JianshuSessionListener {
    void onLogin();

    void onLogout();
  }
}

