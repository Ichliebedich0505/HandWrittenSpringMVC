package servlet;

import annotation.Controller;
import annotation.Qualifier;
import annotation.RequestMapping;
import annotation.Service;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class DispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private HashMap<String, Method> handlerMapping = new HashMap();

    private HashMap<String, Object> ioc = new HashMap<String, Object>();

    private HashMap<String, Object> controllerMap = new HashMap<String, Object>();



    @Override
    public void init(ServletConfig config) throws ServletException {
        // 先加载配置
        doLoadConfig(config.getInitParameter("configLocation"));


        // 然后初始化一些必要的实例
        //      1. 先扫描(比如注解了controller的类)
        try {
            doScanner(properties.getProperty("scanningPath"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        //      2. 再将扫描到的类通过反射实例化
        try {
            doInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // 初始化handlerMapping(SpringBoot框架中MVC的9大组件之一)
        try {
            initHandlerMapping();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        // 由于我们完全不用Spring的框架内容，所以必须自己实现IOC，将某些服务注入到controller中的autowired中
        doIOC();

    }

    private void doIOC() {
        // 处理的上controller内部的autowired的IOC注入（不过这边写成了Qualifier）
        if(ioc.isEmpty()){
            return;
        }

        for(Map.Entry<String, Object> entry : ioc.entrySet()){
            Class clazz = entry.getValue().getClass();

            Field[] fields = clazz.getDeclaredFields();
            for(Field f: fields){
                f.setAccessible(true);
                if(f.isAnnotationPresent(Qualifier.class)){
                    Qualifier q = f.getAnnotation(Qualifier.class);
                    String val = q.value();
                    String key = "";
                    if(!val.equals("")&&val!=""){
                        key = val;
                    }else {
                        key = f.getName();
                    }
                    try {

                        f.set(entry.getValue(), ioc.get(key.replace("u", "U")));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // 这边做测试

    }

    private void initHandlerMapping() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // 获取requestMapping注解
        if(classNames.isEmpty()){
            return;
        }

        // requestMapping一定在controller上，所以要遍历的是ioc表而非再次去遍历classNames
        for(Map.Entry<String, Object> entry : ioc.entrySet()){

            Class clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class))
            {
                continue;
            }
            String baseUrl = "";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping anno = (RequestMapping) clazz.getAnnotation(RequestMapping.class);
                baseUrl = anno.value();
            }
            // 接着便是读取每个函数上要映射的路径了
            Method methods[] = clazz.getMethods();
            for(Method method : methods){
                if(method.isAnnotationPresent(RequestMapping.class)){
                    RequestMapping anno = method.getAnnotation(RequestMapping.class);
                    String url = anno.value();

                    url = baseUrl + url;
                    handlerMapping.put(url, method);

                    Object controller = null;
                    String ctrlName = clazz.getSimpleName();

                    if(ioc.containsKey(ctrlName)){
                        controller = ioc.get(ctrlName);
                    }else {
                        controller = clazz.newInstance();
                    }
                    controllerMap.put(url, controller);
                }
            }
        }
    }

    private void doInstance() throws ClassNotFoundException {

        if(classNames.isEmpty()){
            return;
        }

        for(String fName: classNames){
            Class<?> clazz = Class.forName(fName);

            if(clazz.isAnnotationPresent(Controller.class)){
                try {
                    ioc.put(clazz.getSimpleName(), clazz.newInstance());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else if(clazz.isAnnotationPresent(Service.class)){
                try {
                    String tmp = clazz.getSimpleName().substring(0, clazz.getSimpleName().length()-4);
                    ioc.put(tmp, clazz.newInstance());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                continue;
            }

        }
    }

    private void doScanner(String scanningPath) throws MalformedURLException, URISyntaxException {
        // 填充classNames
        //      用递归的方法遍历所有包
        URL url = null;
        if (scanningPath.equals(".")) {
            url = this.getClass().getClassLoader().getResource("/");
            scanningPath = "/";
        }else {
            url = this.getClass().getClassLoader().getResource(scanningPath);
        }
        File dir = new File(url.getFile());
        for(File f : dir.listFiles()){
            String filename = f.getName();
            if(f.isDirectory()){
                doScanner(scanningPath+ "/" + filename);
            }else {
                // 字节码早就存储了文件，通过classforname扫描文件中的具体内容
                String classname = scanningPath.replaceAll("//", "").replaceAll("/", ".") + "." + filename.replaceAll(".class", "");
                if (!classname.contains("META-INF")&&!classname.contains("application.")) {
                    classNames.add(classname);
                }

            }
        }

    }

    private void doLoadConfig(String configLocation) {

        // 往properties中填充具体配置内容
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(configLocation);
        //InputStreamReader isreader = new InputStreamReader(is);
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if( is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 不做操作
        this.doPost(req, resp);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //
        doDispatch(req, resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {

        // /user/getname
        //1.获取url地址
        String requestURI = req.getRequestURI();
        String eles[] = requestURI.split("/");
        requestURI = "/" + eles[eles.length - 2] + "/" + eles[eles.length - 1];
        if(requestURI == null || requestURI.equals("")) {
            return;
        }
        //2.从handlerMapping集合中使用url地址获取方法
        Method method = handlerMapping.get(requestURI);

        try {
            method.invoke(controllerMap.get(requestURI));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }
}
