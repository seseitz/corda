package net.corda.djvm.rules.implementation

import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.CodeLabel
import net.corda.djvm.code.instructions.TryCatchBlock
import net.corda.djvm.rules.InstructionRule
import net.corda.djvm.validation.RuleContext
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import sandbox.net.corda.djvm.costing.ThresholdViolationException
import sandbox.net.corda.djvm.rules.RuleViolationException

/**
 * Rule that checks for attempted catches of [ThreadDeath], [ThresholdViolationException], [StackOverflowError],
 * [OutOfMemoryError], [Error] or [Throwable].
 */
class DisallowCatchingBlacklistedExceptions : InstructionRule(), Emitter {

    override fun validate(context: RuleContext, instruction: Instruction) = context.validate {
        if (instruction is TryCatchBlock) {
            val typeName = context.classModule.getFormattedClassName(instruction.typeName)
            warn("Injected runtime check for catch-block for type $typeName") given
                    (instruction.typeName in disallowedExceptionTypes)
        }
    }

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is TryCatchBlock && instruction.typeName in disallowedExceptionTypes) {
            handlers.add(instruction.handler)
        } else if (instruction is CodeLabel && isExceptionHandler(instruction.label)) {
            duplicate()
            invokeInstrumenter("checkCatch", "(Ljava/lang/Throwable;)V")
        }
    }

    private val handlers = mutableSetOf<Label>()

    private fun isExceptionHandler(label: Label) = label in handlers

    companion object {
        private val thresholdViolationException: String = Type.getInternalName(ThresholdViolationException::class.java)
        private val ruleViolationException: String = Type.getInternalName(RuleViolationException::class.java)

        private val disallowedExceptionTypes = setOf(
                ruleViolationException,
                thresholdViolationException,

                /**
                 * These errors indicate that the JVM is failing,
                 * so don't allow these to be caught either.
                 */
                "java/lang/StackOverflowError",
                "java/lang/OutOfMemoryError",

                /**
                 * These are immediate super-classes for our explicit errors.
                 */
                "java/lang/VirtualMachineError",
                "java/lang/ThreadDeath",

                /**
                 * Any of [ThreadDeath] and [VirtualMachineError]'s throwable
                 * super-classes need explicit checking.
                 */
                "java/lang/Throwable",
                "java/lang/Error"
        )

    }

}
