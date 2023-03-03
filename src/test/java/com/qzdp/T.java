package com.qzdp;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @author haofeng
 * @date 2022/10/17 8:48
 * @description todo
 */

public class T {
    @Test
    public void a(){
//        "beginTime": "2022-10-16 21:27:18",
//                "endTime": "2022-10-18 21:27:18",
        LocalDateTime begin = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long l1 = begin.toEpochSecond(ZoneOffset.UTC);
        System.out.println(l1);
        LocalDateTime end = LocalDateTime.of(2022, 11, 20, 0, 0, 0);
        long l2 = end.toEpochSecond(ZoneOffset.UTC);
        long l3 = System.currentTimeMillis() / 1000;
        System.out.println(l2);
        System.out.println(System.currentTimeMillis());
        System.out.println(l3);
        System.out.println(LocalDateTime.now());
    }
}
