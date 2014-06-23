package jadx.tests.internal.switches;

import jadx.api.InternalJadxTest;
import jadx.core.dex.nodes.ClassNode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSwitchNoDefault extends InternalJadxTest {

	public static class TestCls {
		public void test(int a) {
			String s = null;
			switch (a) {
				case 1:
					s = "1";
					break;
				case 2:
					s = "2";
					break;
				case 3:
					s = "3";
					break;
				case 4:
					s = "4";
					break;
			}
			System.out.println(s);
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertEquals(4, count(code, "break;"));
		assertEquals(1, count(code, "System.out.println(s);"));
	}
}
