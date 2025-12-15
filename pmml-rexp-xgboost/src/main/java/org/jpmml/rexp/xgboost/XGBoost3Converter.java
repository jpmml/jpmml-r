/*
 * Copyright (c) 2025 Villu Ruusmann
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
package org.jpmml.rexp.xgboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dmg.pmml.DataType;
import org.dmg.pmml.VerificationField;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.Feature;
import org.jpmml.rexp.RExpEncoder;
import org.jpmml.rexp.RGenericVector;

public class XGBoost3Converter extends XGBoostConverter {

	public XGBoost3Converter(RGenericVector booster){
		super(booster);
	}

	@Override
	protected Map<VerificationField, List<?>> encodeActiveValues(RGenericVector dataFrame){
		return encodeVerificationData(dataFrame);
	}

	@Override
	protected String getJsonPath(){
		return "$";
	}

	@Override
	protected List<Feature> aggregateFeatures(List<? extends Feature> features, RExpEncoder encoder){
		List<Feature> result = new ArrayList<>();

		for(int i = 0, max = features.size(); i < max; ){
			Feature feature = features.get(i);

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				String name = binaryFeature.getName();
				DataType dataType = binaryFeature.getDataType();

				List<Object> values = new ArrayList<>();
				values.add(binaryFeature.getValue());

				i++;

				while(i < max){
					Feature nextFeature = features.get(i);

					if(nextFeature instanceof BinaryFeature){
						BinaryFeature nextBinaryFeature = (BinaryFeature)nextFeature;

						String nextName = nextBinaryFeature.getName();

						if(Objects.equals(name, nextName)){
							values.add(nextBinaryFeature.getValue());

							i++;

							continue;
						}
					}

					break;
				}

				CategoricalFeature categoricalFeature = new CategoricalFeature(encoder, name, dataType, values);

				result.add(categoricalFeature);
			} else

			{
				result.add(feature);

				i++;
			}
		}

		return result;
	}
}