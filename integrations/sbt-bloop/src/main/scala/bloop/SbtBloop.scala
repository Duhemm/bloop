package bloop.integrations.sbt

import bloop.integrations.BloopConfig
import sbt.{Keys, AutoPlugin, Def, ScopeFilter, ThisBuild, Compile, Test, Configuration, File}

object SbtBloop extends AutoPlugin {
  import sbt.plugins.JvmPlugin
  override def requires = JvmPlugin
  override def trigger = allRequirements
  final val autoImport = AutoImportedKeys

  override def globalSettings: Seq[Def.Setting[_]] = PluginImplementation.globalSettings
  override def projectSettings: Seq[Def.Setting[_]] = PluginImplementation.projectSettings
}

object AutoImportedKeys {
  import sbt.{TaskKey, taskKey, settingKey, SettingKey}
  val bloopConfigDir: SettingKey[File] =
    settingKey[File]("Directory where to write bloop configuration files")
  val installBloop: TaskKey[Unit] =
    taskKey[Unit]("Generate all bloop configuration files")
}

object PluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = List(
    AutoImportedKeys.installBloop := PluginDefaults.installBloop.value,
    AutoImportedKeys.bloopConfigDir := PluginDefaults.bloopConfigDir.value
  )

  import sbt.inConfig
  private val bloopGenerate: sbt.TaskKey[Unit] =
    sbt.taskKey[Unit]("Generate bloop configuration files for this project")
  val projectSettings: Seq[Def.Setting[_]] = List(Compile, Test).flatMap { conf =>
    inConfig(conf)(List(bloopGenerate := PluginDefaults.bloopGenerate.value))
  }

  object PluginDefaults {
    import sbt.Task
    import bloop.Compat._

    lazy val bloopGenerate: Def.Initialize[Task[Unit]] = Def.task {
      def makeName(name: String, configuration: Configuration): String =
        if (configuration == Compile) name else name + "-test"

      val logger = Keys.streams.value.log
      val project = Keys.thisProject.value
      val configuration = Keys.configuration.value
      val projectName = makeName(project.id, configuration)
      val baseDirectory = Keys.baseDirectory.value.getAbsoluteFile

      // In the test configuration, add a dependency on the base project
      val baseProjectDependency = if (configuration == Test) List(project.id) else Nil

      // TODO: We should extract the right configuration for the dependency.
      val projectDependencies =
        project.dependencies.map(dep => makeName(dep.project.project, configuration))
      val dependencies = projectDependencies ++ baseProjectDependency
      // TODO: We should extract the right configuration for the aggregate.
      val aggregates = project.aggregate.map(agg => makeName(agg.project, configuration))
      val dependenciesAndAggregates = dependencies ++ aggregates

      val scalaName = "scala-compiler"
      val scalaVersion = Keys.scalaVersion.value
      val scalaOrg = Keys.ivyScala.value.map(_.scalaOrganization).getOrElse("org.scala-lang")
      val allScalaJars = Keys.scalaInstance.value.allJars.map(_.getAbsoluteFile)
      val classpath = PluginDefaults.emulateDependencyClasspath.value.map(_.getAbsoluteFile)
      val classesDir = Keys.classDirectory.value.getAbsoluteFile
      val sourceDirs = Keys.sourceDirectories.value
      val testFrameworks = Keys.testFrameworks.value.map(_.implClassNames)
      val scalacOptions = Keys.scalacOptions.value
      val javacOptions = Keys.javacOptions.value
      val tmp = Keys.target.value / "tmp-bloop"
      val bloopConfigDir = AutoImportedKeys.bloopConfigDir.value
      val outFile = bloopConfigDir / s"$projectName.config"

      // Force source and resource generators on this task manually
      // We cannot depend on `managedSources` and `managedResources` because they trigger compilation
      (Keys.sourceGenerators.value ++ Keys.resourceGenerators.value).join.map(_.flatten)

      // format: OFF
      val config = BloopConfig(projectName, baseDirectory, dependenciesAndAggregates, scalaOrg, scalaName,scalaVersion,
        classpath, classesDir, scalacOptions, javacOptions, sourceDirs, testFrameworks, allScalaJars, tmp)
      sbt.IO.createDirectory(bloopConfigDir)
      config.writeTo(outFile)
      logger.success(s"Bloop wrote the configuration of project '$projectName' to '$outFile'.")
      // format: ON
    }

    lazy val installBloop: Def.Initialize[Task[Unit]] = Def.taskDyn {
      val filter = ScopeFilter(sbt.inAnyProject, sbt.inConfigurations(Compile, Test))
      PluginImplementation.bloopGenerate.all(filter).map(_ => ())
    }

    lazy val bloopConfigDir: Def.Initialize[File] = Def.setting {
      (Keys.baseDirectory in ThisBuild).value / ".bloop-config"
    }

    import sbt.Classpaths

    /**
     * Emulates `dependencyClasspath` without triggering compilation of dependent projects.
     *
     * Why do we do this instead of a simple `productDirectories ++ libraryDependencies`?
     * We want the classpath to have the correct topological order of the project dependencies.
     */
    final lazy val emulateDependencyClasspath: Def.Initialize[Task[Seq[File]]] = Def.taskDyn {
      val currentProject = Keys.thisProjectRef.value
      val data = Keys.settingsData.value
      val deps = Keys.buildDependencies.value
      val conf = Keys.classpathConfiguration.value
      val self = Keys.configuration.value

      import scala.collection.JavaConverters._
      val visited = Classpaths.interSort(currentProject, conf, data, deps)
      val productDirs = (new java.util.LinkedHashSet[Task[Seq[File]]]).asScala
      for ((dep, c) <- visited) {
        if ((dep != currentProject) || (conf.name != c && self.name != c)) {
          val classpathKey = (Keys.productDirectories in (dep, sbt.ConfigKey(c)))
          productDirs += classpathKey.get(data).getOrElse(sbt.std.TaskExtra.constant(Nil))
        }
      }

      val internalClasspathTask = (productDirs.toList.join).map(_.flatten.distinct)
      Def.task {
        val internalClasspath = internalClasspathTask.value
        val externalClasspath = Keys.externalDependencyClasspath.value.map(_.data)
        internalClasspath ++ externalClasspath
      }
    }
  }
}
