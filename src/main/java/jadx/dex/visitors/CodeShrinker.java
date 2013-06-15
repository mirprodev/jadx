package jadx.dex.visitors;

import jadx.dex.attributes.AttributeFlag;
import jadx.dex.instructions.ArithNode;
import jadx.dex.instructions.ArithOp;
import jadx.dex.instructions.IfNode;
import jadx.dex.instructions.InsnType;
import jadx.dex.instructions.args.InsnArg;
import jadx.dex.instructions.args.InsnWrapArg;
import jadx.dex.instructions.args.LiteralArg;
import jadx.dex.instructions.args.RegisterArg;
import jadx.dex.nodes.BlockNode;
import jadx.dex.nodes.InsnNode;
import jadx.dex.nodes.MethodNode;
import jadx.utils.BlockUtils;
import jadx.utils.exceptions.JadxRuntimeException;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeShrinker extends AbstractVisitor {

	private static final Logger LOG = LoggerFactory.getLogger(CodeShrinker.class);

	@Override
	public void visit(MethodNode mth) {
		if (mth.isNoCode() || mth.getAttributes().contains(AttributeFlag.DONT_SHRINK))
			return;

		shrink(mth);
		pretify(mth);
	}

	private static void shrink(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			List<InsnNode> insnList = block.getInstructions();
			InstructionRemover remover = new InstructionRemover(insnList);
			for (InsnNode insn : insnList) {
				// wrap instructions
				RegisterArg result = insn.getResult();
				if (result != null) {
					List<InsnArg> useList = result.getTypedVar().getUseList();
					if (useList.size() == 1) {
						// variable is used only in this instruction
						// TODO not correct sometimes :(
						remover.add(insn);
					} else if (useList.size() == 2) {
						InsnArg useInsnArg = selectOther(useList, result);
						InsnNode useInsn = useInsnArg.getParentInsn();
						if (useInsn == null) {
							LOG.debug("parent insn null in " + useInsnArg + " from " + insn + " mth: " + mth);
						} else if (useInsn != insn) {
							boolean wrap = false;
							// TODO
							if (false && result.getTypedVar().getName() != null) {
								// don't wrap if result variable has name from debug info
								wrap = false;
							} else if (BlockUtils.blockContains(block, useInsn)) {
								// TODO don't reorder methods invocations
								// wrap insn from current block
								wrap = true;
							} else {
								// TODO implement rules for shrink insn from different blocks
								BlockNode useBlock = BlockUtils.getBlockByInsn(mth, useInsn);
								if (useBlock != null && useBlock.getPredecessors().contains(block)) {
									wrap = true;
								}
							}
							if (wrap) {
								if (useInsn.getType() == InsnType.MOVE) {
									// TODO
									// remover.add(useInsn);
								} else {
									useInsnArg.wrapInstruction(insn);
									remover.add(insn);
								}
							}
						}
					}
				}
			}
			remover.perform();
		}
	}

	private static void pretify(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			for (int i = 0; i < block.getInstructions().size(); i++) {
				InsnNode insn = block.getInstructions().get(i);

				InsnNode ni = pretifyInsn(mth, insn);
				if (ni != null)
					block.getInstructions().set(i, ni);
			}
		}
	}

	private static InsnNode pretifyInsn(MethodNode mth, InsnNode insn) {
		for (InsnArg arg : insn.getArguments()) {
			if (arg.isInsnWrap()) {
				InsnNode ni = pretifyInsn(mth, ((InsnWrapArg) arg).getWrapInsn());
				if (ni != null)
					arg.wrapInstruction(ni);
			}
		}
		switch (insn.getType()) {
			case ARITH:
				ArithNode arith = (ArithNode) insn;
				if (arith.getArgsCount() == 2) {
					InsnArg litArg = null;

					if (arith.getArg(1).isInsnWrap()) {
						InsnNode wr = ((InsnWrapArg) arith.getArg(1)).getWrapInsn();
						if (wr.getType() == InsnType.CONST)
							litArg = wr.getArg(0);
					} else if (arith.getArg(1).isLiteral()) {
						litArg = arith.getArg(1);
					}

					if (litArg != null) {
						long lit = ((LiteralArg) litArg).getLiteral();
						boolean invert = false;

						if (arith.getOp() == ArithOp.ADD && lit < 0)
							invert = true;

						// fix 'c + (-1)' => 'c - (1)'
						if (invert) {
							return new ArithNode(ArithOp.SUB,
									arith.getResult(), insn.getArg(0),
									InsnArg.lit(-lit, litArg.getType()));
						}
					}
				}
				break;

			case IF:
				// simplify 'cmp' instruction in if condition
				IfNode ifb = (IfNode) insn;
				InsnArg f = ifb.getArg(0);
				if (f.isInsnWrap()) {
					InsnNode wi = ((InsnWrapArg) f).getWrapInsn();
					if (wi.getType() == InsnType.CMP_L || wi.getType() == InsnType.CMP_G) {
						if (ifb.isZeroCmp()
								|| ((LiteralArg) ifb.getArg(1)).getLiteral() == 0) {
							ifb.changeCondition(wi.getArg(0), wi.getArg(1), ifb.getOp());
						} else {
							LOG.warn("TODO: cmp" + ifb);
						}
					}
				}
				break;

			default:
				break;
		}
		return null;
	}

	public static InsnArg inlineArgument(MethodNode mth, RegisterArg arg) {
		InsnNode assignInsn = arg.getAssignInsn();
		if (assignInsn == null)
			return null;

		// recursively wrap all instructions
		List<RegisterArg> list = new ArrayList<RegisterArg>();
		List<RegisterArg> args = mth.getArguments(false);
		int i = 0;
		do {
			list.clear();
			assignInsn.getRegisterArgs(list);
			for (RegisterArg rarg : list) {
				InsnNode ai = rarg.getAssignInsn();
				if (ai != assignInsn && ai != null
						&& rarg.getParentInsn() != ai)
					rarg.wrapInstruction(ai);
			}
			// remove method args
			if (list.size() != 0 && args.size() != 0) {
				list.removeAll(args);
			}
			i++;
			if (i > 1000)
				throw new JadxRuntimeException("Can't inline arguments for: " + arg + " insn: " + assignInsn);
		} while (!list.isEmpty());

		return arg.wrapInstruction(assignInsn);
	}

	private static InsnArg selectOther(List<InsnArg> list, RegisterArg insn) {
		InsnArg first = list.get(0);
		if (first == insn)
			return list.get(1);
		else
			return first;
	}
}
