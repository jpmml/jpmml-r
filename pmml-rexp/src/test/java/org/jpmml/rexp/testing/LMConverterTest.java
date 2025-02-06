/*
 * Copyright (c) 2016 Villu Ruusmann
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
package org.jpmml.rexp.testing;

import org.jpmml.converter.testing.Datasets;
import org.junit.jupiter.api.Test;

public class LMConverterTest extends RExpEncoderBatchTest implements Datasets {

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("LMFormula", AUTO);
	}

	@Test
	public void evaluateMLRFormulaAuto() throws Exception {
		evaluate("WrappedLMFormula", AUTO);
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("LMCustFormula", AUTO);
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("LMFormula", WINE_QUALITY);
	}
}