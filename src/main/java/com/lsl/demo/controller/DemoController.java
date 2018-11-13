package com.lsl.demo.controller;

import com.lsl.demo.annotation.LSLAutowired;
import com.lsl.demo.annotation.LSLController;
import com.lsl.demo.annotation.LSLRequestMapping;
import com.lsl.demo.annotation.LSLRequestParam;
import com.lsl.demo.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@LSLController
public class DemoController {

    @LSLAutowired
    private DemoService demoService;
    @LSLRequestMapping("/demo")
    public void demo(HttpServletRequest req, HttpServletResponse resp, @LSLRequestParam("name") String name) throws Exception{
        System.out.println(name);
        resp.getWriter().write("<h1>I am a boy</h1>");
        demoService.test();
    }
}
