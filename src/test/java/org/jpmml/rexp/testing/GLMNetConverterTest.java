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
package org.jpmml.rexp.testing;

import org.jpmml.converter.testing.Datasets;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.jpmml.rexp.testing.RExpEncoderBatchTest;
import org.junit.Test;

public class GLMNetConverterTest extends RExpEncoderBatchTest implements Datasets {

	@Test
	public void evaluateAuto() throws Exception {
		evaluate("ElNet", AUTO);
	}

	@Test
	public void evaluateWineQuality() throws Exception {
		evaluate("ElNet", WINE_QUALITY);
	}

	@Test
	public void evaluateVisit() throws Exception {
		evaluate("FishNet", VISIT);
	}

	@Test
	public void evaluateLogNetWineColor() throws Exception {
		evaluate("LogNet", WINE_COLOR, new PMMLEquivalence(5e-11, 5e-11));
	}

	@Test
	public void evaluateMultNetIris() throws Exception {
		evaluate("MultNet", IRIS);
	}

	@Test
	public void evaluateMultNetWineColor() throws Exception {
		evaluate("MultNet", WINE_COLOR, new PMMLEquivalence(5e-10, 5e-10));
	}
}