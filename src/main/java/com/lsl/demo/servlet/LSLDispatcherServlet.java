package com.lsl.demo.servlet;

import com.lsl.demo.annotation.LSLAutowired;
import com.lsl.demo.annotation.LSLController;
import com.lsl.demo.annotation.LSLRequestMapping;
import com.lsl.demo.annotation.LSLService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class LSLDispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    //跟web.xml的init-param一致
    private static final String LOCATION = "contextConfigLocation";

    //保存所有的配置信息
    private Properties p = new Properties();

    //保存被扫描的类名
    private List<String> className = new ArrayList<String>();

    //核心IOC容器
    private Map<String, Object> ioc = new HashMap<>();

    //保存url和具体方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2.扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

//        //3.初始化所有相关的类，并保存在ioc容器中
        doInstance();

//        //4.依赖注入
        doAutowired();

//        //5.构造HandlerMapping
        initHandlerMapping();

        //6.等待请求，匹配url，定位方法，反射调用执行
        //调用doGet或doPost方法

        //提示信息
        System.out.println("mvcframework is init successful");

    }

    private void doLoadConfig(String location) {
        InputStream fis = null;
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(location);
            //读取配置文件
            p.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String packageName) {
        //将所有的包路径转换为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            //如果是文件夹，继续递归
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                className.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    /**
     * 类名首字母变成小写方法
     *
     * @param str
     * @return
     */
    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * bean默认的key是类名首字母小写
     * 将组建放到ioc容器中
     */
    private void doInstance() {
        if (className.size() == 0) {
            return;
        }
        try {
            for (String className : className) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(LSLController.class)) {
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(LSLService.class)) {
                    LSLService service = clazz.getAnnotation(LSLService.class);
                    String beanName = service.value();
                    //判断用户是否自己设置了名字
                    if (!"".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }
                    //如何没有自己设置，就按接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }

                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到实例对象中的所有成员变量
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(LSLAutowired.class)) {
                    continue;
                }
                LSLAutowired autowired = field.getAnnotation(LSLAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);//设置私有属性的访问权限
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(LSLController.class)) {
                continue;
            }
            String baseUrl = "";
            //获取Controller的url
            if (clazz.isAnnotationPresent(LSLRequestMapping.class)) {
                LSLRequestMapping requestMapping = clazz.getAnnotation(LSLRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取Method的url
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {

                //方法上没有RequestMapping注解的直接跳过
                if (!method.isAnnotationPresent(LSLRequestMapping.class)) {
                    continue;
                }

                LSLRequestMapping requestMapping = method.getAnnotation(LSLRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("mapped" + url + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (handlerMapping.isEmpty()) {
            return;
        }
        String url = req.getRequestURI();
        System.out.println(url);
        String contextPath = req.getContextPath();
        System.out.println(contextPath);
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!!");
            return;
        }
        //获取请求的参数
        Map<String, String[]> paramMap = req.getParameterMap();
        //通过url映射到具体的方法
        Method method = handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        //保存参数
        Object[] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            //根据参数名称，做某些处理
            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                //参数类型已确定
                paramValues[i] = req;
                continue;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
                continue;
            } else if (parameterType == String.class) {
                for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }

        }
        try {
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            //利用反射机制调用
            method.invoke(ioc.get(beanName), paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}