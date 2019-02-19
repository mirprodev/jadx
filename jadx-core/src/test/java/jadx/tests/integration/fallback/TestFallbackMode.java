package jadx.tests.integration.fallback;

import org.junit.Test;

import jadx.core.dex.nodes.ClassNode;
import jadx.tests.api.IntegrationTest;

import static jadx.tests.api.utils.JadxMatchers.containsOne;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class TestFallbackMode extends IntegrationTest {

	public static class TestCls {

		public int test(int a) {
			while (a < 10) {
				a++;
			}
			return a;
		}
	}

	@Test
	public void test() {
		setFallback();
		disableCompilation();

		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();

		assertThat(code, containsString("public int test(int r2) {"));
		assertThat(code, containsOne("r1 = this;"));
		assertThat(code, containsOne("L_0x0000:"));
		assertThat(code, containsOne("L_0x0007:"));
		assertThat(code, containsOne("int r2 = r2 + 1"));
		assertThat(code, not(containsString("throw new UnsupportedOperationException")));
	}
}
