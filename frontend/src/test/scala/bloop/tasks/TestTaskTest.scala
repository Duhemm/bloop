package bloop.tasks

import bloop.{DynTest, Project}
import bloop.engine.State
import bloop.reporter.ReporterConfig
import sbt.testing.Framework
import bloop.engine.tasks.Tasks
import bloop.testing.TestInternals
import xsbti.compile.CompileAnalysis

object TestTaskTest extends DynTest {
  private val TestProjectName = "with-tests"
  private val (testState: State, testProject: Project, testAnalysis: CompileAnalysis) = {
    import bloop.util.JavaCompat.EnrichOptional
    val target = s"$TestProjectName-test"
    val state0 = ProjectHelpers.loadTestProject(TestProjectName)
    val project = state0.build.getProjectFor(target).getOrElse(sys.error(s"Missing $target!"))
    val state = Tasks.compile(state0, project, ReporterConfig.defaultFormat)
    val result = state.results.getResult(project).analysis().toOption
    val analysis = result.getOrElse(sys.error(s"$target lacks analysis after compilation!?"))
    (state, project, analysis)
  }

  test("Test project can be selected with or without `-test` suffix") {
    val withoutSuffix = Tasks.pickTestProject(TestProjectName, testState)
    val withSuffix = Tasks.pickTestProject(s"$TestProjectName-test", testState)
    assert(withoutSuffix.isDefined)
    assert(withoutSuffix.get.name == "with-tests-test")
    assert(withSuffix.isDefined)
    assert(withSuffix.get.name == "with-tests-test")
  }

  import java.net.URLClassLoader
  private val testLoader: ClassLoader = {
    val classpath = Tasks.constructClasspath(testProject)
    new URLClassLoader(classpath, TestInternals.filteredLoader)
  }

  private val frameworks: Array[Framework] = {
    testProject.testFrameworks.flatMap(n =>
      TestInternals.getFramework(testLoader, n.toList, testState.logger))
  }

  // Test that frameworks are class-loaded, detected and that test classes exist and can be run.
  val frameworkNames = List("ScalaTest", "ScalaCheck", "Specs2", "UTest")
  frameworkNames.foreach { framework =>
    test(s"$framework's tests are detected") {
      testState.logger.quietIfSuccess { logger =>
        val discovered = Tasks.discoverTests(testAnalysis, frameworks)
        val testNames = discovered.valuesIterator.flatMap(defs => defs.map(_.fullyQualifiedName()))
        assert(testNames.exists(_.contains(s"${framework}Test")))
      }
    }

    test(s"$framework tests can run") {
      testState.logger.quietIfSuccess { logger =>
        val discovered = Tasks.discoverTests(testAnalysis, frameworks).toList
        val toRun = discovered.flatMap {
          case (framework, taskDefs) =>
            val testName = s"${framework.name()}Test"
            val filteredDefs = taskDefs.filter(_.fullyQualifiedName.contains(testName))
            if (filteredDefs.isEmpty) Nil
            else {
              val runner = TestInternals.getRunner(framework, testLoader)
              val tasks = runner.tasks(filteredDefs.toArray).toList
              List(() => TestInternals.executeTasks(tasks, Tasks.eventHandler, logger))
            }
        }

        toRun.foreach(thunk => thunk())
      }
    }
  }
}
