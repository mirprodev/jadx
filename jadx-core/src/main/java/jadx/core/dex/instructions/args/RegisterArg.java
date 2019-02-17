package jadx.core.dex.instructions.args;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jadx.core.dex.nodes.DexNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.utils.InsnUtils;

public class RegisterArg extends InsnArg implements Named {
	public static final String THIS_ARG_NAME = "this";

	protected final int regNum;
	// not null after SSATransform pass
	private SSAVar sVar;

	public RegisterArg(int rn) {
		this.regNum = rn;
	}

	public RegisterArg(int rn, ArgType type) {
		this.type = type;
		this.regNum = rn;
	}

	public int getRegNum() {
		return regNum;
	}

	@Override
	public boolean isRegister() {
		return true;
	}

	@Override
	public void setType(ArgType type) {
		if (sVar != null) {
			sVar.setType(type);
		}
	}

	@Override
	public ArgType getType() {
		SSAVar ssaVar = this.sVar;
		if (ssaVar != null) {
			return ssaVar.getTypeInfo().getType();
		}
		return ArgType.UNKNOWN;
	}

	public ArgType getInitType() {
		return type;
	}

	@Override
	public boolean isTypeImmutable() {
		if (sVar != null) {
			RegisterArg assign = sVar.getAssign();
			if (assign == this) {
				return false;
			}
			return assign.isTypeImmutable();
		}
		return false;
	}

	public SSAVar getSVar() {
		return sVar;
	}

	void setSVar(@NotNull SSAVar sVar) {
		this.sVar = sVar;
	}

	public String getName() {
		if (isThis()) {
			return THIS_ARG_NAME;
		}
		if (sVar == null) {
			return null;
		}
		return sVar.getName();
	}

	public void setName(String name) {
		if (sVar != null && name != null) {
			sVar.setName(name);
		}
	}

	public boolean isNameEquals(InsnArg arg) {
		String n = getName();
		if (n == null || !(arg instanceof Named)) {
			return false;
		}
		return n.equals(((Named) arg).getName());
	}

	public void mergeName(InsnArg arg) {
		if (arg instanceof Named) {
			Named otherArg = (Named) arg;
			String otherName = otherArg.getName();
			String name = getName();
			if (!Objects.equals(name, otherName)) {
				if (name == null) {
					setName(otherName);
				} else if (otherName == null) {
					otherArg.setName(name);
				}
			}
		}
	}

	@Override
	public RegisterArg duplicate() {
		return duplicate(getRegNum(), sVar);
	}

	public RegisterArg duplicate(int regNum, @Nullable SSAVar sVar) {
		RegisterArg dup = new RegisterArg(regNum, getInitType());
		if (sVar != null) {
			dup.setSVar(sVar);
		}
		dup.copyAttributesFrom(this);
		return dup;
	}

	/**
	 * Return constant value from register assign or null if not constant
	 */
	public Object getConstValue(DexNode dex) {
		InsnNode parInsn = getAssignInsn();
		if (parInsn == null) {
			return null;
		}
		return InsnUtils.getConstValueByInsn(dex, parInsn);
	}

	@Nullable
	public InsnNode getAssignInsn() {
		if (sVar == null) {
			return null;
		}
		return sVar.getAssign().getParentInsn();
	}

	public boolean equalRegister(RegisterArg arg) {
		return regNum == arg.regNum;
	}

	public boolean sameRegAndSVar(InsnArg arg) {
		if (!arg.isRegister()) {
			return false;
		}
		RegisterArg reg = (RegisterArg) arg;
		return regNum == reg.getRegNum()
				&& Objects.equals(sVar, reg.getSVar());
	}

	public boolean equalRegisterAndType(RegisterArg arg) {
		return regNum == arg.regNum && type.equals(arg.type);
	}

	public boolean sameCodeVar(RegisterArg arg) {
		return this.getSVar().getCodeVar() == arg.getSVar().getCodeVar();
	}

	@Override
	public int hashCode() {
		return regNum;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof RegisterArg)) {
			return false;
		}
		RegisterArg other = (RegisterArg) obj;
		return regNum == other.regNum
				&& Objects.equals(getType(), other.getType())
				&& Objects.equals(sVar, other.getSVar());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(r");
		sb.append(regNum);
		if (sVar != null) {
			sb.append('v').append(sVar.getVersion());
		}
		if (getName() != null) {
			sb.append(" '").append(getName()).append('\'');
		}
		ArgType type = getType();
		sb.append(' ').append(type);
		ArgType initType = getInitType();
		if (!type.equals(initType) && !type.isTypeKnown()) {
			sb.append(" I:").append(initType);
		}
		if (!isAttrStorageEmpty()) {
			sb.append(' ').append(getAttributesString());
		}
		sb.append(")");
		return sb.toString();
	}
}
