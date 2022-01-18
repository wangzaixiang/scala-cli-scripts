// using scala 2.13
// using lib com.lihaoyi::mainargs:0.2.2
// using lib com.lihaoyi::os-lib:0.8.0
// using lib org.scalameta::svm-subs:20.2.0
// using lib org.ow2.asm:asm:9.2

import mainargs.{ParserForMethods, main}
import org.objectweb.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}
import os.Path

import java.io.InputStream
import java.util.jar.{JarEntry, JarFile}
import scala.jdk.CollectionConverters._

object asm_demo1 {

  class MyClassVisitor(jarPath: String) extends ClassVisitor(Opcodes.ASM9) {

    var className: String = _

    override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]): Unit = {
      this.className = name.replace('/', '.')
    }

    override def visitMethod(access: Int, name: String, descriptor: String, signature: String, exceptions: Array[String]): MethodVisitor = {
      new MyMethodVisitor(name)
    }

    class MyMethodVisitor(name: String) extends MethodVisitor(Opcodes.ASM9) {
      override def visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean): Unit = {
        if (isInterface && owner.startsWith("com/xdd/shop/remote")) {
          println(s"${jarPath} ${Console.BLUE}=>${Console.RESET} ${MyClassVisitor.this.className}.${this.name} ${Console.BLUE}=>${Console.RESET} ${owner.replace('/', '.')}.$name")
        }
      }
    }
  }

  def checkClassFile(jarFile: String, input: InputStream): Unit =
    try {
      val reader = new ClassReader(input)
      reader.accept(new MyClassVisitor(jarFile), 0)
    }
    finally {
      input.close()
    }

  def checkJar(jarPath: String) = try {
    val jarFile = new JarFile(jarPath)
    val path = Path(jarPath, os.pwd)
    jarFile.entries().asScala.foreach { entry: JarEntry =>
      if (entry.getName.endsWith(".class")) {
        checkClassFile(path.last, jarFile.getInputStream(entry))
      }
    }
  }
  catch {
    case ex => println(s"open $jarPath failed ${ex.toString}")
  }


  @main
  def scanDir(dir: String): Unit = {

    os.walk(Path(dir, os.pwd)).filter(p => p.last.endsWith(".jar"))
      .foreach { path =>
        checkJar(path.toString())
      }

  }

  @main
  def scanJars(jars: Seq[String]): Unit = {
    jars.foreach { jar =>
      checkJar(jar)
    }
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)

}
