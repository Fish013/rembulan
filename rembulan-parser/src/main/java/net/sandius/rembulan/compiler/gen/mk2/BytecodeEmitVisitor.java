package net.sandius.rembulan.compiler.gen.mk2;

import net.sandius.rembulan.compiler.CodeVisitor;
import net.sandius.rembulan.compiler.analysis.SlotAllocInfo;
import net.sandius.rembulan.compiler.analysis.TypeInfo;
import net.sandius.rembulan.compiler.gen.ClassNameTranslator;
import net.sandius.rembulan.compiler.gen.asm.ASMUtils;
import net.sandius.rembulan.compiler.gen.asm.BoxedPrimitivesMethods;
import net.sandius.rembulan.compiler.gen.asm.ConversionMethods;
import net.sandius.rembulan.compiler.gen.asm.DispatchMethods;
import net.sandius.rembulan.compiler.gen.asm.InvokeKind;
import net.sandius.rembulan.compiler.gen.asm.LuaStateMethods;
import net.sandius.rembulan.compiler.gen.asm.ObjectSinkMethods;
import net.sandius.rembulan.compiler.gen.asm.UpvalueMethods;
import net.sandius.rembulan.compiler.ir.*;
import net.sandius.rembulan.core.ExecutionContext;
import net.sandius.rembulan.core.LuaState;
import net.sandius.rembulan.core.ObjectSink;
import net.sandius.rembulan.core.Upvalue;
import net.sandius.rembulan.util.Check;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.sandius.rembulan.compiler.gen.asm.DispatchMethods.*;
import static org.objectweb.asm.Opcodes.*;

class BytecodeEmitVisitor extends CodeVisitor {

	private final ASMBytecodeEmitter context;
	private final RunMethod runMethod;

	private final SlotAllocInfo slots;
	private final TypeInfo types;

	private final Map<Object, LabelNode> labels;
	private final ArrayList<LabelNode> resumptionPoints;

	private final InsnList il;
	private final List<LocalVariableNode> locals;

	public BytecodeEmitVisitor(ASMBytecodeEmitter context, RunMethod runMethod, SlotAllocInfo slots, TypeInfo types) {
		this.context = Check.notNull(context);
		this.runMethod = Check.notNull(runMethod);
		this.slots = Check.notNull(slots);
		this.types = Check.notNull(types);

		this.labels = new HashMap<>();
		this.resumptionPoints = new ArrayList<>();

		this.il = new InsnList();
		this.locals = new ArrayList<>();
	}

	public InsnList instructions() {
		return il;
	}

	public List<LocalVariableNode> locals() {
		return locals;
	}

	protected int slot(AbstractVal v) {
		return runMethod.slotOffset() + slots.slotOf(v);
	}

	protected int slot(Var v) {
		return runMethod.slotOffset() + slots.slotOf(v);
	}

	protected int nextLocalVariableIndex() {
		return runMethod.slotOffset() + slots.numSlots();
	}

	private LabelNode l(Object o) {
		LabelNode l = labels.get(o);

		if (l != null) {
			return l;
		}
		else {
			LabelNode nl = new LabelNode();
			labels.put(o, nl);
			return nl;
		}
	}

	public AbstractInsnNode loadExecutionContext() {
		return new VarInsnNode(ALOAD, runMethod.LV_CONTEXT);
	}

	private AbstractInsnNode loadState() {
		return new MethodInsnNode(
				INVOKEINTERFACE,
				Type.getInternalName(ExecutionContext.class),
				"getState",
				Type.getMethodDescriptor(
						Type.getType(LuaState.class)),
				true);
	}

	private AbstractInsnNode loadSink() {
		return new MethodInsnNode(
				INVOKEINTERFACE,
				Type.getInternalName(ExecutionContext.class),
				"getObjectSink",
				Type.getMethodDescriptor(
						Type.getType(ObjectSink.class)),
				true);
	}

	private InsnList loadLuaState() {
		InsnList il = new InsnList();
		il.add(loadExecutionContext());
		il.add(loadState());
		return il;
	}

	public InsnList retrieve_0() {
		InsnList il = new InsnList();

		il.add(loadExecutionContext());
		il.add(loadSink());
		il.add(ObjectSinkMethods.get(0));

		return il;
	}

	public InsnList loadUpvalueRef(UpVar uv) {
		InsnList il = new InsnList();

		il.add(new VarInsnNode(ALOAD, 0));
		il.add(new FieldInsnNode(
				GETFIELD,
				context.thisClassType().getInternalName(),
				context.getUpvalueFieldName(uv),
				Type.getDescriptor(Upvalue.class)));

		return il;
	}

	class ResumptionPoint {

		public final int index;

		private ResumptionPoint(int index) {
			this.index = index;
		}

		public LabelNode label() {
			return l(this);
		}

		public InsnList save() {
			InsnList il = new InsnList();
			il.add(ASMUtils.loadInt(index));
			il.add(new VarInsnNode(ISTORE, runMethod.LV_RESUME));
			return il;
		}

		public InsnList resume() {
			InsnList il = new InsnList();

			il.add(label());
			il.add(ASMUtils.frameSame());

			return il;
		}
	}

	public ResumptionPoint resumptionPoint() {
		int idx = resumptionPoints.size();
		ResumptionPoint rp = new ResumptionPoint(idx);
		resumptionPoints.add(rp.label());
		return rp;
	}

	@Override
	public void visit(PhiStore node) {
		il.add(new VarInsnNode(ALOAD, slot(node.src())));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(PhiLoad node) {
		il.add(new VarInsnNode(ALOAD, slot(node.src())));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(VarInit node) {
		if (types.isReified(node.var())) {
			il.add(loadLuaState());
			il.add(new VarInsnNode(ALOAD, slot(node.var())));
			il.add(LuaStateMethods.newUpvalue());
			il.add(new VarInsnNode(ASTORE, slot(node.var())));
		}
		else {
			il.add(new VarInsnNode(ALOAD, slot(node.src())));
			il.add(new VarInsnNode(ASTORE, slot(node.var())));
		}
	}

	@Override
	public void visit(VarStore node) {
		if (types.isReified(node.var())) {
			il.add(new VarInsnNode(ALOAD, slot(node.var())));
			il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Upvalue.class)));
			il.add(new VarInsnNode(ALOAD, slot(node.src())));
			il.add(UpvalueMethods.set());
		}
		else {
			il.add(new VarInsnNode(ALOAD, slot(node.src())));
			il.add(new VarInsnNode(ASTORE, slot(node.var())));
		}
	}

	@Override
	public void visit(VarLoad node) {
		if (types.isReified(node.var())) {
			il.add(new VarInsnNode(ALOAD, slot(node.var())));
			il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Upvalue.class)));
			il.add(UpvalueMethods.get());
		}
		else {
			il.add(new VarInsnNode(ALOAD, slot(node.var())));
		}
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(UpLoad node) {
		il.add(loadUpvalueRef(node.upval()));
		il.add(UpvalueMethods.get());
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(UpStore node) {
		il.add(loadUpvalueRef(node.upval()));
		il.add(new VarInsnNode(ALOAD, slot(node.src())));
		il.add(UpvalueMethods.set());
	}

	@Override
	public void visit(LoadConst.Nil node) {
		il.add(new InsnNode(ACONST_NULL));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(LoadConst.Bool node) {
		il.add(BoxedPrimitivesMethods.loadBoxedBoolean(node.value()));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(LoadConst.Int node) {
		il.add(ASMUtils.loadLong(node.value()));
		il.add(BoxedPrimitivesMethods.box(Type.LONG_TYPE, Type.getType(Long.class)));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(LoadConst.Flt node) {
		il.add(ASMUtils.loadDouble(node.value()));
		il.add(BoxedPrimitivesMethods.box(Type.DOUBLE_TYPE, Type.getType(Double.class)));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(LoadConst.Str node) {
		il.add(new LdcInsnNode(node.value()));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	private static String dispatchMethodName(BinOp.Op op) {
		switch (op) {
			case ADD:    return OP_ADD;
			case SUB:    return OP_SUB;
			case MUL:    return OP_MUL;
			case MOD:    return OP_MOD;
			case POW:    return OP_POW;
			case DIV:    return OP_DIV;
			case IDIV:   return OP_IDIV;
			case BAND:   return OP_BAND;
			case BOR:    return OP_BOR;
			case BXOR:   return OP_BXOR;
			case SHL:    return OP_SHL;
			case SHR:    return OP_SHR;

			case CONCAT: return OP_CONCAT;

			case EQ:     return OP_EQ;
			case NEQ:    return OP_NEQ;
			case LT:     return OP_LT;
			case LE:     return OP_LE;

			default:     throw new IllegalArgumentException("Illegal binary operation: " + op);
		}
	}

	private static String dispatchMethodName(UnOp.Op op) {
		switch (op) {
			case UNM:  return OP_UNM;
			case BNOT: return OP_BNOT;
			case LEN:  return OP_LEN;
			default:   throw new IllegalArgumentException("Illegal unary operation: " + op);
		}
	}

	@Override
	public void visit(BinOp node) {
		ResumptionPoint rp = resumptionPoint();
		il.add(rp.save());

		il.add(loadExecutionContext());
		il.add(new VarInsnNode(ALOAD, slot(node.left())));
		il.add(new VarInsnNode(ALOAD, slot(node.right())));
		il.add(DispatchMethods.dynamic(dispatchMethodName(node.op()), 2));

		il.add(rp.resume());
		il.add(retrieve_0());
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(UnOp node) {
		if (node.op() == UnOp.Op.NOT) {
			il.add(new VarInsnNode(ALOAD, slot(node.arg())));
			il.add(ConversionMethods.booleanValueOf());
			il.add(BoxedPrimitivesMethods.box(Type.BOOLEAN_TYPE, Type.getType(Boolean.class)));
		}
		else {
			ResumptionPoint rp = resumptionPoint();
			il.add(rp.save());

			il.add(loadExecutionContext());
			il.add(new VarInsnNode(ALOAD, slot(node.arg())));
			il.add(DispatchMethods.dynamic(dispatchMethodName(node.op()), 1));

			il.add(rp.resume());
			il.add(retrieve_0());
		}

		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(TabNew node) {
		il.add(loadLuaState());
		il.add(LuaStateMethods.newTable(node.array(), node.hash()));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(TabGet node) {
		ResumptionPoint rp = resumptionPoint();
		il.add(rp.save());

		il.add(loadExecutionContext());
		il.add(new VarInsnNode(ALOAD, slot(node.obj())));
		il.add(new VarInsnNode(ALOAD, slot(node.key())));
		il.add(DispatchMethods.index());

		il.add(rp.resume());
		il.add(retrieve_0());
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(TabSet node) {
		ResumptionPoint rp = resumptionPoint();
		il.add(rp.save());

		il.add(loadExecutionContext());
		il.add(new VarInsnNode(ALOAD, slot(node.obj())));
		il.add(new VarInsnNode(ALOAD, slot(node.key())));
		il.add(new VarInsnNode(ALOAD, slot(node.value())));
		il.add(DispatchMethods.newindex());

		il.add(rp.resume());
	}

	@Override
	public void visit(TabSetInt node) {
		ResumptionPoint rp = resumptionPoint();
		il.add(rp.save());

		// TODO: only used in constructors: can use rawset directly instead of boxing a constant

		il.add(loadExecutionContext());
		il.add(new VarInsnNode(ALOAD, slot(node.obj())));
		il.add(ASMUtils.loadLong(node.idx()));
		il.add(BoxedPrimitivesMethods.box(Type.LONG_TYPE, Type.getType(Long.class)));
		il.add(new VarInsnNode(ALOAD, slot(node.value())));
		il.add(DispatchMethods.newindex());

		il.add(rp.resume());
	}

	@Override
	public void visit(TabStackAppend node) {
		throw new UnsupportedOperationException("tabstackappend"); // TODO
	}

	@Override
	public void visit(Vararg node) {
		il.add(loadExecutionContext());
		il.add(loadSink());
		il.add(new VarInsnNode(ALOAD, runMethod.LV_VARARGS));
		il.add(ObjectSinkMethods.setTo(0));
	}

	@Override
	public void visit(Ret node) {
		// TODO
		il.add(new InsnNode(RETURN));
	}

	@Override
	public void visit(TCall node) {
		// TODO
		il.add(new InsnNode(RETURN));
	}

	@Override
	public void visit(Call node) {
		ResumptionPoint rp = resumptionPoint();
		il.add(rp.save());

		VList args = node.args();

		if (args.isMulti()) {
			// variable number of arguments, stored on stack

			if (args.addrs().size() == 0) {
				// no prefix, simply take the stack contents
				il.add(loadExecutionContext());
				il.add(new VarInsnNode(ALOAD, slot(node.fn())));  // call target

				// stack contents as an array
				il.add(loadExecutionContext());
				il.add(loadSink());
				il.add(ObjectSinkMethods.toArray());

				il.add(DispatchMethods.call(0));
			}
			else {
				// a non-empty prefix followed by the stack contents

				LabelNode begin = new LabelNode();
				LabelNode end = new LabelNode();

				int lv_idx_stack = nextLocalVariableIndex();
				int lv_idx_args = nextLocalVariableIndex() + 1;

				Type arrayType = ASMUtils.arrayTypeFor(Object.class);

				locals.add(new LocalVariableNode("stack", arrayType.getDescriptor(), null, begin, end, lv_idx_stack));
				locals.add(new LocalVariableNode("args", arrayType.getDescriptor(), null, begin, end, lv_idx_args));

				il.add(begin);

				// get stack contents as an array
				il.add(loadExecutionContext());
				il.add(loadSink());
				il.add(ObjectSinkMethods.toArray());
				il.add(new VarInsnNode(ASTORE, lv_idx_stack));

				// compute the overall arg list length
				il.add(new VarInsnNode(ALOAD, lv_idx_stack));
				il.add(new InsnNode(ARRAYLENGTH));
				il.add(ASMUtils.loadInt(args.addrs().size()));
				il.add(new InsnNode(IADD));

				// instantiate the actual arg list (length is on stack top)
				il.add(new TypeInsnNode(ANEWARRAY, Type.getInternalName(Object.class)));
				il.add(new VarInsnNode(ASTORE, lv_idx_args));

				// fill in the prefix
				int idx = 0;
				for (Val v : args.addrs()) {
					il.add(new VarInsnNode(ALOAD, lv_idx_args));
					il.add(ASMUtils.loadInt(idx++));
					il.add(new VarInsnNode(ALOAD, slot(v)));
					il.add(new InsnNode(AASTORE));
				}

				// call System.arraycopy(stack, 0, args, prefix_length, stack.length)
				il.add(new VarInsnNode(ALOAD, lv_idx_stack));
				il.add(ASMUtils.loadInt(0));
				il.add(new VarInsnNode(ALOAD, lv_idx_args));
				il.add(ASMUtils.loadInt(args.addrs().size()));
				il.add(new VarInsnNode(ALOAD, lv_idx_stack));
				il.add(new InsnNode(ARRAYLENGTH));
				il.add(new MethodInsnNode(
						INVOKESTATIC,
						Type.getInternalName(System.class),
						"arraycopy",
						Type.getMethodDescriptor(
								Type.VOID_TYPE,
								Type.getType(Object.class), Type.INT_TYPE, Type.getType(Object.class), Type.INT_TYPE, Type.INT_TYPE),
						false));

				// dispatch the call
				il.add(loadExecutionContext());
				il.add(new VarInsnNode(ALOAD, slot(node.fn())));  // target
				il.add(new VarInsnNode(ALOAD, lv_idx_args));  // arguments

				il.add(DispatchMethods.call(0));

				il.add(end);
			}
		}
		else {
			// fixed number of arguments

			il.add(loadExecutionContext());

			int k = DispatchMethods.adjustKind_call(InvokeKind.encode(args.addrs().size(), false));
			if (k > 0) {
				// pass arguments on the JVM stack
				il.add(new VarInsnNode(ALOAD, slot(node.fn())));
				for (Val v : args.addrs()) {
					il.add(new VarInsnNode(ALOAD, slot(v)));
				}
			}
			else {
				// pass arguments packed in an array
				il.add(ASMUtils.loadInt(args.addrs().size()));
				il.add(new TypeInsnNode(ANEWARRAY, Type.getInternalName(Object.class)));

				int idx = 0;
				for (Val v : args.addrs()) {
					il.add(new InsnNode(DUP));
					il.add(ASMUtils.loadInt(idx++));
					il.add(new VarInsnNode(ALOAD, slot(v)));
					il.add(new InsnNode(AASTORE));
				}
			}

			il.add(DispatchMethods.call(k));
		}

		il.add(rp.resume());
	}

	@Override
	public void visit(StackGet node) {
		il.add(loadExecutionContext());
		il.add(loadSink());
		il.add(ObjectSinkMethods.get(node.idx()));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(Label node) {
		il.add(l(node));
		il.add(ASMUtils.frameSame());
	}

	@Override
	public void visit(Jmp node) {
		il.add(new JumpInsnNode(GOTO, l(node.jmpDest())));
	}

	@Override
	public void visit(Closure node) {
		ClassNameTranslator tr = context.classNameTranslator;

		Type fnType = Type.getType(node.id().toClassName(tr));

		il.add(new TypeInsnNode(NEW, fnType.getInternalName()));
		il.add(new InsnNode(DUP));
		for (AbstractVar var : node.args()) {
			if (var instanceof UpVar) {
				il.add(loadUpvalueRef((UpVar) var));
			}
			else {
				Var v = (Var) var;
				assert (types.isReified(v));
				il.add(new VarInsnNode(ALOAD, slot(v)));
				il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Upvalue.class)));
			}
		}

		Type[] ctorArgTypes = new Type[node.args().size()];
		Arrays.fill(ctorArgTypes, Type.getType(Upvalue.class));

		il.add(ASMUtils.ctor(fnType, ctorArgTypes));

		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(ToNumber node) {
		il.add(new VarInsnNode(ALOAD, slot(node.dest())));
		il.add(ConversionMethods.toNumericalValue(""));
		il.add(new VarInsnNode(ASTORE, slot(node.dest())));
	}

	@Override
	public void visit(ToNext node) {
		// no-op
	}

	private LabelNode dest;

	@Override
	public void visit(Branch branch) {
		assert (dest == null);
		try {
			dest = l(branch.jmpDest());
			branch.condition().accept(this);
		}
		finally {
			dest = null;
		}
	}

	@Override
	public void visit(Branch.Condition.Nil cond) {
		assert (dest != null);
		il.add(new VarInsnNode(ALOAD, slot(cond.addr())));
		il.add(new JumpInsnNode(IFNULL, dest));
	}

	@Override
	public void visit(Branch.Condition.Bool cond) {
		assert (dest != null);
		il.add(new VarInsnNode(ALOAD, slot(cond.addr())));
		il.add(ConversionMethods.booleanValueOf());
		il.add(new JumpInsnNode(cond.expected() ? IFNE : IFEQ, dest));
	}

	@Override
	public void visit(Branch.Condition.NumLoopEnd cond) {
		assert (dest != null);
		il.add(new VarInsnNode(ALOAD, slot(cond.var())));
		il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Number.class)));
		il.add(new VarInsnNode(ALOAD, slot(cond.limit())));
		il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Number.class)));
		il.add(new VarInsnNode(ALOAD, slot(cond.step())));
		il.add(new TypeInsnNode(CHECKCAST, Type.getInternalName(Number.class)));
		il.add(DispatchMethods.continueLoop());
		il.add(new JumpInsnNode(IFEQ, dest));
	}

	@Override
	public void visit(CPUWithdraw node) {
		// TODO
	}

}
