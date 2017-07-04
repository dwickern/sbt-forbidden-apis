package com.github.dwickern.forbiddenapis

import java.net.URLClassLoader

import de.thetaphi.forbiddenapis.Checker
import de.thetaphi.forbiddenapis.Checker.Option._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{Def, _}

object SbtForbiddenApisPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object ForbiddenApisKeys {
    val signaturesFiles = settingKey[Set[File]]("Custom signature files")
    val signaturesURLs = settingKey[Set[URL]]("Custom signature file URLs")
    val signatures = settingKey[Seq[String]]("Custom signature listing")
    val bundledSignatures = settingKey[Set[String]]("Names of built-in signature files")
    val suppressAnnotations = settingKey[Set[String]]("Class name of a custom Java annotation that are used in the checked code to suppress errors.")
    val targetCompatibility = settingKey[Option[String]]("The compiler target version used to expand references to bundled JDK signatures.")
    val failOnMissingClasses = settingKey[Boolean]("Fail the build, if a referenced class is missing.")
    val ignoreFailures = settingKey[Boolean]("Don't fail the build if a violation is found.")
    val failOnUnresolvableSignatures = settingKey[Boolean]("Fail the build if a signature is not resolving.")
    val disableClassloadingCache = settingKey[Boolean]("Disable the internal JVM classloading cache when getting bytecode from the classpath.")
    val failOnUnsupportedJava = settingKey[Boolean]("Fail the build if the bundled ASM library cannot read the class file format of the runtime library or the runtime library cannot be discovered.")
  }

  object autoImport {
    val forbiddenApis = ForbiddenApisKeys
    val checkForbiddenApis = taskKey[Unit]("Check if the project contains calls to forbidden APIs")
  }

  import autoImport._
  import forbiddenApis._

  override lazy val projectSettings = Seq(
    signaturesFiles := Set.empty,
    signaturesURLs := Set.empty,
    signatures := Seq.empty,
    bundledSignatures := Set.empty,
    suppressAnnotations := Set.empty,
    targetCompatibility := None,
    failOnUnsupportedJava := false,
    failOnMissingClasses := true,
    failOnUnresolvableSignatures := true,
    ignoreFailures := false,
    disableClassloadingCache := false,
    checkForbiddenApis := check.value
  )

  private def check: Def.Initialize[Task[Unit]] = Def.task {
    val log = new de.thetaphi.forbiddenapis.Logger {
      private val logger = streams.value.log("forbidden-apis")
      def error(msg: String): Unit = logger.error(msg)
      def warn(msg: String): Unit = logger.warn(msg)
      def info(msg: String): Unit = logger.info(msg)
    }

    val loader = {
      val urls = (fullClasspath in Compile).value.map(_.data.toURI.toURL).toArray
      URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader)
    }

    val options = {
      val o = java.util.EnumSet.noneOf(classOf[Checker.Option])
      if (failOnMissingClasses.value)
        o.add(FAIL_ON_MISSING_CLASSES)
      if (!ignoreFailures.value)
        o.add(FAIL_ON_VIOLATION)
      if (failOnUnresolvableSignatures.value)
        o.add(FAIL_ON_UNRESOLVABLE_SIGNATURES)
      if (disableClassloadingCache.value)
        o.add(DISABLE_CLASSLOADING_CACHE)
      o
    }

    val checker = new Checker(log, loader, options)

    if (!checker.isSupportedJDK) {
      val msg = s"Your Java runtime (${System.getProperty("java.runtime.name")} ${System.getProperty("java.runtime.version")}) " +
        s"is not supported by the forbiddenapis plugin. Please run the checks with a supported JDK!"

      if (failOnUnsupportedJava.value)
        throw new Exception(msg)
      else {
        log.warn(msg)
      }
    } else {
      suppressAnnotations.value.foreach(checker.addSuppressAnnotation)

      if (targetCompatibility.value.isEmpty) {
        log.warn(s"The '${targetCompatibility.key.label}' project or task property is missing. " +
          "Trying to read bundled JDK signatures without compiler target. " +
          "You have to explicitly specify the version in the resource name.")
      }

      bundledSignatures.value.foreach(checker.addBundledSignatures(_, targetCompatibility.value.orNull))
      signaturesFiles.value.foreach(checker.parseSignaturesFile)
      signaturesURLs.value.foreach(checker.parseSignaturesFile)

      if (signatures.value.nonEmpty) {
        val sig = signatures.value.mkString(System.getProperty("line.separator", "\n"))
        checker.parseSignaturesString(sig)
      }

      if (checker.hasNoSignatures) {
        if (options.contains(FAIL_ON_UNRESOLVABLE_SIGNATURES))
          throw new Exception(s"No API signatures found; use '${signatures.key.label}', '${bundledSignatures.key.label}', '${signaturesURLs.key.label}', and/or '${signaturesFiles.key.label}' to define those!")
        else {
          log.info("Skipping execution because no API signatures are available.")
        }
      } else {
        val classFiles = (compile in Compile).value.stamps.allProducts
        classFiles.foreach(checker.addClassToCheck)

        checker.run()
      }
    }
  }
}
