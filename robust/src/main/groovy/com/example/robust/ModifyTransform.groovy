package com.example.robust

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.Project

/**
 * 这代表是一个处理程序，需要先把class字节码文件加载到内存
 */
class ModifyTransform extends Transform {

    /**
     * 字节码池,用于将类文件的字节码加入到内存中
     */

    def pool = ClassPool.default


    //groovy是弱语言类型 ，def类似于 var
    def project

    ModifyTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        //这里就是要返回文件夹，会在transforms的目录下创建对应的文件夹，不要和已存在的名字重复
        return "jone"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        //上层文件输入的文件类型，如Class,dex,jar。
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        //要处理的范围，是module，还是整个项目,这里选择整个项目
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        //是否是增量编译，false表示每次都编译 ；true编译修改之后的内容，一般都是选择false
        return false
    }

    /**
     * 类似于 butterKnife中的process()核心类
     * @param transformInvocation
     * @throws TransformException* @throws InterruptedException* @throws IOException
     */
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        //将工程中的所有类的字节码都加载到池子中，这里的内存是PC端内存，这也是为什么AS如此的耗内存，
        // 因为在每个Transform中都有一个自己的字节码池，没有它就无法进行编译，加载，以及修改
        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
        }

        //当前程序只处理class文件，一般在jar包中，以及单独存在的class文件。对于jar包直接拷贝到下一个transform中的文件夹
        transformInvocation.inputs.each {
            //jar包可能存在多个，都不需要处理
            it.jarInputs.each {
                pool.insertClassPath(it.file.absolutePath)
                //直接找到下一个文件夹，直接输出
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.JAR)
                //dest这里表示目的地，it.file是原文件
                FileUtils.copyFile(it.file, dest)
            }

            //遍历文件
            it.directoryInputs.each {
                //preFileName 是绝对路径，代表的是上一个文件输入的路径，它是文件夹名，不是class文件名,
                ///Users/renxianju/Android_Projects/RoboustFix/app/build/intermediates/javac/debug/classes（截止到包名之前）
                //这里之所以要绝对路径，因为后面class的内加载到内存中时，需要用到全类名，需要去去除前面的路径，并把'/'换成'.'，获取到包名，在拼接文件名，
                ///com.roboustfix.***.class
                def preFileName = it.file.absolutePath
                pool.insertClassPath(preFileName)
                //真正处理需要的逻辑
                findTarget(it.file, preFileName)

                //在这里插入我们自己的代码，需要先把class字节码文件加载到内存
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                //dest这里表示目的地，it.file是原文件
                FileUtils.copyDirectory(it.file, dest)
            }
        }


    }


    private void findTarget(File dir, String preFileName) {
        //递归寻找class结尾的字节码文件
        if (dir.isDirectory()) {
            dir.listFiles().each {
                findTarget(it, preFileName)
            }
        } else {
            //判断是否是.class文件
            modify(dir, preFileName)
        }
    }


    private void modify(File dir, String fileName) {
        def filePath = dir.absolutePath
        if (!filePath.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }

        if (filePath.contains('R$') || filePath.contains('R.class')
                || filePath.contains('BuildConfig.class')) {
            return
        }

        //获取全类名，以全类名为key,去ClassPool中找到字节码对象CtClass，然后修改
        //第一个replace去除前面的绝对路径，第二个表示是window的文件表示"\\"，第三replace表示mac的"/"，替换成.
        //.com.manu.robustfix.MainActivity.class
        def className = filePath.replace(fileName, "").replace("\\", ".")
                .replace("/", ".")
        //去除.class后缀,以及前面的.
        def name = className.replace(SdkConstants.DOT_CLASS, "").substring(1)
        CtClass ctClass = pool.get(name)
        //如果是包目录下的类，就加入这些代码，进行if判断，这里是写死的。
        // 美团是直接在项目中配置了一个xml文件，通过读取xml文件内的配置，来对对应的方法添加逻辑
        if (name.contains("com.roboustfix")) {
            def body = "if(com.roboustfix.PatchProxy.isSupport()){}"
            addCode(ctClass, body, fileName)
        }
    }

    private void addCode(CtClass ctClass, String body, String fileName) {
        if (ctClass.getName().contains("PatchProxy")) {
            return  //防止递归调用，直接栈溢出
        }
        CtMethod[] methods = ctClass.getDeclaredMethods() //这里的CtMethod不同于 java中的Method
        for (method in methods) {
            if (method.getName().contains("isSupport")) {
                continue
            }
            method.insertBefore(body)//插入到方法前
            //method.insertAt("")  插入到第几行，第几个问题
        }
        //插入代码之后，一定要重写将ctClass重新写入文件中
        ctClass.writeFile(fileName)
        ctClass.detach()//释放内存

    }
}