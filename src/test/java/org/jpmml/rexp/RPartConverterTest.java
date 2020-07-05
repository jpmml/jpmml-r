/*
 * Copyright (c) 2018 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import org.junit.Test;

public class RPartConverterTest extends RExpTest {

	@Test
	public void evaluateAudit() throws Exception {
		evaluate("RPart", "Audit");
	}

	@Test
	public void evaluateAuditNA() throws Exception {
		evaluate("RPart", "AuditNA");
	}

	@Test
	public void evaluateAuto() throws Exception {
		evaluate("RPart", "Auto");
	}

	@Test
	public void evaluateAutoNA() throws Exception {
		evaluate("RPart", "AutoNA");
	}

	@Test
	public void evaluateIris() throws Exception {
		evaluate("RPart", "Iris");
	}

	@Test
	public void evaluateIrisNA() throws Exception {
		evaluate("RPart", "IrisNA");
	}

	@Test
	public void evaluateCaretIris() throws Exception {
		evaluate("TrainRPart", "Iris");
	}

	@Test
	public void evaluateWineQuality() throws Exception {
		evaluate("RPart", "WineQuality");
	}

	@Test
	public void evaluateWineQualityNA() throws Exception {
		evaluate("RPart", "WineQualityNA");
	}

	@Test
	public void evaluateWineColor() throws Exception {
		evaluate("RPart", "WineColor");
	}

	@Test
	public void evaluateWineColorNA() throws Exception {
		evaluate("RPart", "WineColorNA");
	}
}