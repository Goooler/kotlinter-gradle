package org.jmailen.gradle.kotlinter.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jmailen.gradle.kotlinter.support.KotlinterError
import org.jmailen.gradle.kotlinter.tasks.format.FormatWorkerAction
import javax.inject.Inject

open class FormatTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : ConfigurableKtLintTask(
    projectLayout = projectLayout,
    objectFactory = objectFactory,
) {

    @OutputFile
    @Optional
    val report: RegularFileProperty = objectFactory.fileProperty()

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run(inputChanges: InputChanges) {
        val result = with(workerExecutor.noIsolation()) {
            submit(FormatWorkerAction::class.java) { p ->
                p.name.set(name)
                p.files.from(source)
                p.projectDirectory.set(projectLayout.projectDirectory.asFile)
                p.output.set(report)
                p.changedEditorConfigFiles.from(getChangedEditorconfigFiles(inputChanges))
            }
            runCatching { await() }
        }

        result.exceptionOrNull()?.workErrorCauses<KotlinterError>()?.ifNotEmpty {
            forEach { logger.error(it.message, it.cause) }
            throw GradleException("error formatting sources for $name")
        }
    }
}
