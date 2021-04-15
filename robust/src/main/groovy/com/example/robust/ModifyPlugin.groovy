package com.example.robust

import org.gradle.api.Plugin
import org.gradle.api.Project

class ModifyPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
       println("测试groovy")
        //注册自定义的ModifyTransform,然后把project传入，代表从整个工程找到对应的文件
        project.android.registerTransform(new ModifyTransform(project))
    }
}