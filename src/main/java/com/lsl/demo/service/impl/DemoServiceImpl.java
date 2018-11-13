package com.lsl.demo.service.impl;

import com.lsl.demo.annotation.LSLService;
import com.lsl.demo.service.DemoService;
@LSLService
public class DemoServiceImpl implements DemoService {
    @Override
    public void test() {
        System.out.println("我被调用了");
    }
}
