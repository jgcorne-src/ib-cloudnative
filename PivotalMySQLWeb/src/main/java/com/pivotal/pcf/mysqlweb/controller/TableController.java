/*
PivotalMySQLWeb

Copyright (c) 2017-Present Pivotal Software, Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.pivotal.pcf.mysqlweb.controller;

import com.pivotal.pcf.mysqlweb.beans.Result;
import com.pivotal.pcf.mysqlweb.beans.WebResult;
import com.pivotal.pcf.mysqlweb.dao.PivotalMySQLWebDAOFactory;
import com.pivotal.pcf.mysqlweb.dao.generic.GenericDAO;
import com.pivotal.pcf.mysqlweb.dao.tables.Table;
import com.pivotal.pcf.mysqlweb.dao.tables.TableDAO;
import com.pivotal.pcf.mysqlweb.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Controller
public class TableController
{
    @GetMapping(value = "/tables")
    public String showTables
            (Model model, HttpServletResponse response, HttpServletRequest request, HttpSession session) throws Exception
    {

        if (Utils.verifyConnection(response, session))
        {
            log.info("user_key is null OR Connection stale so new Login required");
            return null;
        }

        String schema = null;
        WebResult tableStructure, tableDetails, tableIndexes;

        log.info("Received request to show tables");

        TableDAO tableDAO = PivotalMySQLWebDAOFactory.getTableDAO();
        GenericDAO genericDAO = PivotalMySQLWebDAOFactory.getGenericDAO();

        String selectedSchema = request.getParameter("selectedSchema");
        log.info("selectedSchema = " + selectedSchema);

        if (selectedSchema != null)
        {
            schema = selectedSchema;
        }
        else
        {
            schema = (String) session.getAttribute("schema");
        }

        log.info("schema = " + schema);

        String tabAction = request.getParameter("tabAction");
        Result result = new Result();

        if (tabAction != null)
        {
            log.info("tabAction = " + tabAction);
            result = null;

            if (tabAction.equalsIgnoreCase("STRUCTURE"))
            {

                tableStructure =
                        tableDAO.getTableStructure
                                (schema,
                                        (String)request.getParameter("tabName"),
                                        (String)session.getAttribute("user_key"));


                model.addAttribute("tableStructure", tableStructure);
                model.addAttribute("tablename", (String)request.getParameter("tabName"));
            }
            else if (tabAction.equalsIgnoreCase("DETAILS"))
            {
                tableDetails =
                        tableDAO.getTableDetails
                                (schema,
                                        (String)request.getParameter("tabName"),
                                        (String)session.getAttribute("user_key"));


                model.addAttribute("tableDetails", tableDetails);
                model.addAttribute("tablename", (String)request.getParameter("tabName"));
            }
            else if (tabAction.equalsIgnoreCase("DDL"))
            {
                String ddl = tableDAO.runShowQuery(schema,
                                                   (String)request.getParameter("tabName"),
                                                   (String)session.getAttribute("user_key"));

                model.addAttribute("tableDDL", ddl.trim());
                model.addAttribute("tablename", (String)request.getParameter("tabName"));
            }
            else if (tabAction.equalsIgnoreCase("INDEXES"))
            {
                tableIndexes = tableDAO.showIndexes(schema,
                        (String)request.getParameter("tabName"),
                        (String)session.getAttribute("user_key"));

                model.addAttribute("tableIndexes", tableIndexes);
                model.addAttribute("tablename", (String)request.getParameter("tabName"));
            }
            else
            {
                result =
                        tableDAO.simpletableCommand
                                (schema,
                                        (String)request.getParameter("tabName"),
                                        tabAction,
                                        (String)session.getAttribute("user_key"));
                model.addAttribute("result", result);

                if (result.getMessage().startsWith("SUCCESS"))
                {
                    if (tabAction.equalsIgnoreCase("DROP"))
                    {
                        session.setAttribute("schemaMap",
                                             genericDAO.populateSchemaMap
                                               ((String)session.getAttribute("schema"),
                                                (String)session.getAttribute("user_key")));
                    }
                }
            }
        }

        List<Table> tbls = tableDAO.retrieveTableList
                  (schema, null, (String)session.getAttribute("user_key"));

        model.addAttribute("records", tbls.size());
        model.addAttribute("estimatedrecords", tbls.size());
        model.addAttribute("tables", tbls);

        model.addAttribute
                ("schemas",
                 genericDAO.allSchemas((String) session.getAttribute("user_key")));

        model.addAttribute("chosenSchema", schema);

        return "tables";
    }

    @PostMapping(value = "/tables")
    public String performTableAction
            (Model model, HttpServletResponse response, HttpServletRequest request, HttpSession session) throws Exception
    {
        if (Utils.verifyConnection(response, session))
        {
            log.info("user_key is null OR Connection stale so new Login required");
            return null;
        }

        String schema = null;
        Result result = new Result();
        List<Table> tbls = null;

        log.info("Received request to perform an action on the tables");

        TableDAO tableDAO = PivotalMySQLWebDAOFactory.getTableDAO();
        GenericDAO genericDAO = PivotalMySQLWebDAOFactory.getGenericDAO();

        String selectedSchema = request.getParameter("selectedSchema");
        log.info("selectedSchema = " + selectedSchema);

        if (selectedSchema != null)
        {
            schema = selectedSchema;
        }
        else
        {
            schema = (String) session.getAttribute("schema");
        }

        log.info("schema = " + schema);

        if (request.getParameter("searchpressed") != null)
        {
            tbls = tableDAO.retrieveTableList
                            (schema,
                            (String)request.getParameter("search"),
                            (String)session.getAttribute("user_key"));

            model.addAttribute("search", (String)request.getParameter("search"));
        }
        else
        {
            String[] tableList  = request.getParameterValues("selected_tbl[]");
            String   commandStr = request.getParameter("submit_mult");

            log.info("tableList = " + Arrays.toString(tableList));
            log.info("command = " + commandStr);

            // start actions now if tableList is not null

            if (tableList != null)
            {
                List al = new ArrayList<Result>();
                for (String table: tableList)
                {
                    result = null;
                    result = tableDAO.simpletableCommand
                            (schema,
                                    table,
                                    commandStr,
                                    (String)session.getAttribute("user_key"));

                    al.add(result);
                }

                model.addAttribute("arrayresult", al);
            }

            tbls = tableDAO.retrieveTableList
                            (schema, null, (String)session.getAttribute("user_key"));
        }

        model.addAttribute("records", tbls.size());
        model.addAttribute("estimatedrecords", tbls.size());
        model.addAttribute("tables", tbls);

        model.addAttribute
                ("schemas", genericDAO.allSchemas((String) session.getAttribute("user_key")));

        model.addAttribute("chosenSchema", schema);

        return "tables";
    }
}
