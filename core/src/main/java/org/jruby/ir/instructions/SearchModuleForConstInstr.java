package org.jruby.ir.instructions;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.UndefinedValue;
import org.jruby.ir.operands.Variable;
import org.jruby.ir.persistence.IRReaderDecoder;
import org.jruby.ir.persistence.IRWriterEncoder;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.opto.ConstantCache;
import org.jruby.runtime.opto.Invalidator;
import org.jruby.util.ByteList;

/**
 * Search for a constant within the current module.  If it cannot find then
 * call const_missing.
 */
public class SearchModuleForConstInstr extends OneOperandResultBaseInstr implements FixedArityInstr {
    ByteList constName;
    private final boolean noPrivateConsts;
    private final boolean callConstMissing;

    // Constant caching
    private volatile transient ConstantCache cache;

    public SearchModuleForConstInstr(Variable result, Operand currentModule, ByteList constName, boolean noPrivateConsts) {
        this(result, currentModule, constName, noPrivateConsts, true);
    }

    public SearchModuleForConstInstr(Variable result, Operand currentModule, ByteList constName,
                                     boolean noPrivateConsts, boolean callConstMissing) {
        super(Operation.SEARCH_MODULE_FOR_CONST, result, currentModule);

        this.constName = constName;
        this.noPrivateConsts = noPrivateConsts;
        this.callConstMissing = callConstMissing;
    }

    public Operand getCurrentModule() {
        return getOperand1();
    }

    public String getConstName() {
        return constName.toString();
    }

    public ByteList getConstByteName() {
        return constName;
    }

    public boolean isNoPrivateConsts() {
        return noPrivateConsts;
    }

    public boolean callConstMissing() {
        return callConstMissing;
    }

    @Override
    public Instr clone(CloneInfo ii) {
        return new SearchModuleForConstInstr(ii.getRenamedVariable(result),
                getCurrentModule().cloneForInlining(ii), constName, noPrivateConsts, callConstMissing);
    }

    @Override
    public String[] toStringNonOperandArgs() {
        return new String[] { "name: " + constName, "no_priv: " + noPrivateConsts};
    }

    private Object cache(Ruby runtime, RubyModule module) {
        String id = getConstName();
        Object constant = noPrivateConsts ? module.getConstantFromNoConstMissing(id, false) : module.getConstantNoConstMissing(id);
        if (constant != null) {
            Invalidator invalidator = runtime.getConstantInvalidator(id);
            cache = new ConstantCache((IRubyObject)constant, invalidator.getData(), invalidator, module.hashCode());
        }
        return constant;
    }

    @Override
    public void encode(IRWriterEncoder e) {
        super.encode(e);
        e.encode(getCurrentModule());
        e.encode(getConstByteName());
        e.encode(isNoPrivateConsts());
        e.encode(callConstMissing());
    }

    public static SearchModuleForConstInstr decode(IRReaderDecoder d) {
        return new SearchModuleForConstInstr(d.decodeVariable(), d.decodeOperand(), d.decodeByteList(),
                d.decodeBoolean(), d.decodeBoolean());
    }

    @Override
    public Object interpret(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        Object cmVal = getCurrentModule().retrieve(context, self, currScope, currDynScope, temp);

        if (!(cmVal instanceof RubyModule)) throw context.runtime.newTypeError(cmVal + " is not a type/class");

        RubyModule module = (RubyModule) cmVal;
        ConstantCache cache = this.cache;
        Object result = !ConstantCache.isCachedFrom(module, cache) ? cache(context.runtime, module) : cache.value;

        if (result == null) {
            if (callConstMissing) {
                result = module.callMethod(context, "const_missing", context.runtime.newSymbol(constName));
            } else {
                result = UndefinedValue.UNDEFINED;
            }
        }

        return result;
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.SearchModuleForConstInstr(this);
    }
}
