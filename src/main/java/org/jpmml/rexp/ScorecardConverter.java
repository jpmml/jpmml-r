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
package org.jpmml.rexp;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.scorecard.Attribute;
import org.dmg.pmml.scorecard.Characteristic;
import org.dmg.pmml.scorecard.Characteristics;
import org.dmg.pmml.scorecard.Scorecard;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;

public class ScorecardConverter extends GLMConverter {

	public ScorecardConverter(RGenericVector glm){
		super(glm);
	}

	@Override
	public Scorecard encodeModel(Schema schema){
		RGenericVector glm = getObject();

		RDoubleVector coefficients = glm.getDoubleElement("coefficients");
		RGenericVector family = glm.getGenericElement("family");
		RGenericVector scConf = DecorationUtil.getGenericElement(glm, "sc.conf");

		Double intercept = coefficients.getElement(LMConverter.INTERCEPT, true);

		List<? extends Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		RNumberVector<?> odds = scConf.getNumericElement("odds");
		RNumberVector<?> basePoints = scConf.getNumericElement("base_points");
		RNumberVector<?> pdo = scConf.getNumericElement("pdo");

		double factor = (pdo.asScalar()).doubleValue() / Math.log(2);

		Map<FieldName, Characteristic> fieldCharacteristics = new LinkedHashMap<>();

		for(Feature feature : features){
			FieldName name = feature.getName();

			if(!(feature instanceof BinaryFeature)){
				throw new IllegalArgumentException();
			}

			Double coefficient = getFeatureCoefficient(feature, coefficients);

			Characteristic characteristic = fieldCharacteristics.get(name);
			if(characteristic == null){
				characteristic = new Characteristic()
					.setName("score(" + FeatureUtil.getName(feature) + ")");

				fieldCharacteristics.put(name, characteristic);
			}

			BinaryFeature binaryFeature = (BinaryFeature)feature;

			SimplePredicate simplePredicate = new SimplePredicate()
				.setField(binaryFeature.getName())
				.setOperator(SimplePredicate.Operator.EQUAL)
				.setValue(binaryFeature.getValue());

			Attribute attribute = new Attribute()
				.setPartialScore(formatScore(-1d * coefficient * factor))
				.setPredicate(simplePredicate);

			characteristic.addAttributes(attribute);
		}

		Characteristics characteristics = new Characteristics();

		Collection<Map.Entry<FieldName, Characteristic>> entries = fieldCharacteristics.entrySet();
		for(Map.Entry<FieldName, Characteristic> entry : entries){
			Characteristic characteristic = entry.getValue();

			Attribute attribute = new Attribute()
				.setPartialScore(0d)
				.setPredicate(new True());

			characteristic.addAttributes(attribute);

			characteristics.addCharacteristics(characteristic);
		}

		Scorecard scorecard = new Scorecard(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), characteristics)
			.setInitialScore(formatScore((basePoints.asScalar()).doubleValue() - Math.log((odds.asScalar()).doubleValue()) * factor - (intercept != null ? intercept * factor : 0)))
			.setUseReasonCodes(false);

		return scorecard;
	}

	static
	private Double formatScore(Double score){
		return (double)Math.round(score);
	}
}
