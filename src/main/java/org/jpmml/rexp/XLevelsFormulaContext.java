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

import java.util.List;

public class XLevelsFormulaContext implements FormulaContext {

	private RGenericVector xlevels = null;


	public XLevelsFormulaContext(RGenericVector xlevels){
		setXLevels(xlevels);
	}

	@Override
	public List<String> getCategories(String variable){
		RGenericVector xlevels = getXLevels();

		if(xlevels != null && xlevels.hasValue(variable)){
			RStringVector levels = (RStringVector)xlevels.getValue(variable);

			return levels.getValues();
		}

		return null;
	}

	@Override
	public RGenericVector getData(){
		return null;
	}

	public RGenericVector getXLevels(){
		return this.xlevels;
	}

	private void setXLevels(RGenericVector xlevels){
		this.xlevels = xlevels;
	}
}