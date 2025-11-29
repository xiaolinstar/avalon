/**
 * 阿瓦隆游戏应用主入口类
 * 负责启动Spring Boot应用程序
 */
package cn.xiaolin.avalon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AvalonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvalonApplication.class, args);
    }

}