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

import java.util.List;
import java.util.Map;

import org.dmg.pmml.VerificationField;
import org.jpmml.converter.Feature;
import org.jpmml.rexp.RExpEncoder;
import org.jpmml.rexp.RGenericVector;
import org.jpmml.xgboost.FeatureMapUtil;

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
		return FeatureMapUtil.aggregateFeatures(features, encoder);
	}
}