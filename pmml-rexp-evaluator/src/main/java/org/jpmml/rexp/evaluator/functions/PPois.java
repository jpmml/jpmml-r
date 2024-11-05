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
package org.jpmml.rexp.evaluator.functions;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.FieldValueUtil;
import org.jpmml.evaluator.TypeInfos;
import org.jpmml.evaluator.functions.AbstractFunction;
import org.jpmml.rexp.evaluator.RExpFunctions;

public class PPois extends AbstractFunction {

	public PPois(){
		super(RExpFunctions.STATS_PPOIS, Arrays.asList("x", "lambda"));
	}

	@Override
	public FieldValue evaluate(List<FieldValue> arguments){
		checkFixedArityArguments(arguments, 2);

		Integer x = getRequiredArgument(arguments, 0).asInteger();
		Double lambda = getRequiredArgument(arguments, 1).asDouble();

		Double result = ppois(x, lambda);

		return FieldValueUtil.create(TypeInfos.CONTINUOUS_DOUBLE, result);
	}

	static
	public double ppois(double x, double lambda){
		x = Math.floor(x + 1e-7);

		GammaDistribution gammaDistribution = new GammaDistribution(x + 1d, 1d);

		return Math.log(gammaDistribution.cumulativeProbability(lambda));
	}
}