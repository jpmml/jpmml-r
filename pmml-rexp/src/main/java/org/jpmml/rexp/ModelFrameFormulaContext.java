/*
 * Copyright (c) 2017 Villu Ruusmann
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

import java.util.List;

public class ModelFrameFormulaContext implements FormulaContext {

	private RGenericVector model = null;


	public ModelFrameFormulaContext(RGenericVector model){
		setModel(model);
	}

	@Override
	public List<String> getCategories(String variable){
		RVector<?> data = getData(variable);

		if(data instanceof RFactorVector){
			RFactorVector factor = (RFactorVector)data;

			return factor.getLevelValues();
		}

		return null;
	}

	@Override
	public RVector<?> getData(String variable){
		RGenericVector model = getModel();

		if(model.hasElement(variable)){
			RVector<?> vector = model.getVectorElement(variable);

			return vector;
		}

		return null;
	}

	public RGenericVector getModel(){
		return this.model;
	}

	private void setModel(RGenericVector model){
		this.model = model;
	}
}