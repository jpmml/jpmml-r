/*
 * Copyright (c) 2024 Villu Ruusmann
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
import org.junit.Test;

public class POLRConverterTest extends RExpEncoderBatchTest implements Datasets {

	@Test
	public void evaluateLogisticWineQuality() throws Exception {
		evaluate("POLR" + "Logistic", WINE_QUALITY);
	}

	@Test
	public void evaluateProbitWineQuality() throws Exception {
		evaluate("POLR" + "Probit", WINE_QUALITY);
	}

	@Test
	public void evaluateLogLogWuneQuality() throws Exception {
		evaluate("POLR" + "LogLog", WINE_QUALITY);
	}

	@Test
	public void evaluateCLogLogWineQuality() throws Exception {
		evaluate("POLR" + "CLogLog", WINE_QUALITY);
	}

	@Test
	public void evaluateCauchitWineQuality() throws Exception {
		evaluate("POLR" + "Cauchit", WINE_QUALITY);
	}
}