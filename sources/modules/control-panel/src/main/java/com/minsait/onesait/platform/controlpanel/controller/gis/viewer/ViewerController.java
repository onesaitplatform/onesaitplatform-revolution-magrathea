/**
 * Copyright Indra Soluciones Tecnologías de la Información, S.L.U.
 * 2013-2019 SPAIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.minsait.onesait.platform.controlpanel.controller.gis.viewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.util.StringUtils;

import com.minsait.onesait.platform.config.model.BaseLayer;
import com.minsait.onesait.platform.config.model.Layer;
import com.minsait.onesait.platform.config.model.Rollback;
import com.minsait.onesait.platform.config.model.User;
import com.minsait.onesait.platform.config.model.Viewer;
import com.minsait.onesait.platform.config.services.gis.layer.LayerService;
import com.minsait.onesait.platform.config.services.gis.viewer.ViewerService;
import com.minsait.onesait.platform.config.services.user.UserService;
import com.minsait.onesait.platform.controlpanel.controller.rollback.RollbackController;
import com.minsait.onesait.platform.controlpanel.helper.gis.viewer.ViewerHelper;
import com.minsait.onesait.platform.controlpanel.rest.management.login.LoginManagementController;
import com.minsait.onesait.platform.controlpanel.rest.management.login.model.RequestLogin;
import com.minsait.onesait.platform.controlpanel.utils.AppWebUtils;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/viewers")
@Slf4j
public class ViewerController {

	private static final  String REDIRECT = "redirect";
	private static final String LAYER_SELECTED_HIDDEN = "layersSelectedHidden";
	private static final String REDIRECT_CONTROLPANEL_VIEWERS_LIST = "/controlpanel/viewers/list";
	private static final String REDIRECT_VIEWERS = "redirect:/viewers/";
	private static final String LOGIN_PATH = "/login";

	@Value("${onesaitplatform.controlpanel.url:http://localhost:18000/controlpanel}")
	private String basePath;

	@Value("${onesaitplatform.webproject.baseurl:http://localhost:18000/web}")
	private String webProjectPath;

	@Autowired
	private AppWebUtils utils;

	@Autowired
	private ViewerService viewerService;

	@Autowired
	private LayerService layerService;

	@Autowired
	private UserService userService;

	@Autowired
	private ViewerHelper viewerHelper;

	@Autowired
	private RollbackController rollbackController;

	@Autowired
	private LoginManagementController controller;

	private static final String NOTPERMISSION = "User has not permission";

	private static final String STATUS = "status";
	private static final String ERROR_STATUS = "error";
	private static final String OK_STATUS = "ok";
	private static final String CAUSE = "cause";

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@GetMapping(value = "/list", produces = "text/html")
	public String list(Model model, HttpServletRequest request) {

		final List<Viewer> viewers = viewerService.findAllViewers(utils.getUserId());
		model.addAttribute("viewers", viewers);
		return "viewers/list";
	}

	@GetMapping(value = "/{id}/login", produces = "text/html")
	public String login(Model model, HttpServletRequest request, @PathVariable("id") String id) {

		model.addAttribute("user", new User());
		return "viewers/login";
	}

	@PostMapping(value = "/{id}/login")
	public ResponseEntity<Map<String, String>> doLogin(Model model, HttpServletRequest request,
			@PathVariable("id") String id) {
		final Map<String, String> response = new HashMap<>();
		String username = request.getParameter("username");
		String password = request.getParameter("password");

		if (!StringUtils.isEmpty(password) && !StringUtils.isEmpty(username)) {
			RequestLogin oauthRequest = new RequestLogin();
			oauthRequest.setPassword(password);
			oauthRequest.setUsername(username);
			try {

				request.getSession().setAttribute("oauthToken",
						(Serializable) (this.controller.postLoginOauth2(oauthRequest).getBody()));

				response.put(REDIRECT, "/controlpanel/viewers/view/" + id);
				response.put(STATUS, OK_STATUS);
				return new ResponseEntity<>(response, HttpStatus.CREATED);
			} catch (Exception e) {
				log.error("Error login user to show viewer. {}", e);
				response.put(REDIRECT, "/403");
				response.put(STATUS, ERROR_STATUS);
				return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
			}
		}
		return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);

	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@GetMapping(value = "/create")
	public String create(Model model) {
		Map<String, String> layersTypes = layerService.getLayersTypes(utils.getUserId());
		List<BaseLayer> baseLayers = viewerService.findAllBaseLayers();
		model.addAttribute("layersTypes", layersTypes);
		model.addAttribute("baseLayers", baseLayers);
		model.addAttribute("viewer", new ViewerDTO());
		return "viewers/create";
	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@PostMapping(value = "/create")
	@Transactional
	public ResponseEntity<Map<String, String>> createViewer(org.springframework.ui.Model model,
			@Valid ViewerDTO viewerDTO, BindingResult bindingResult, RedirectAttributes redirect,
			HttpServletRequest httpServletRequest) {
		final Map<String, String> response = new HashMap<>();
		if (bindingResult.hasErrors()) {
			response.put(STATUS, ERROR_STATUS);
			response.put(CAUSE, utils.getMessage("ontology.validation.error", "validation error"));
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		Viewer viewer = new Viewer();

		User user = userService.getUser(utils.getUserId());
		viewer.setUser(user);
		viewer.setIdentification(viewerDTO.getIdentification());
		viewer.setDescription(viewerDTO.getDescription());
		viewer.setJs(httpServletRequest.getParameter("jsViewer"));

		String layers = httpServletRequest.getParameter(LAYER_SELECTED_HIDDEN);
		String[] split = null;
		split = (layers != null) ? layers.split(",") : new String[0];

		for (int i = 0; i < split.length; i++) {
			Layer layer = layerService.findByIdentification(split[i]);
			viewer.getLayers().add(layer);

			layer.getViewers().add(viewer);
		}

		viewerService.create(viewer, viewerDTO.getBaseLayer());

		response.put(REDIRECT, REDIRECT_CONTROLPANEL_VIEWERS_LIST);
		response.put(CAUSE, OK_STATUS);
		return new ResponseEntity<>(response, HttpStatus.CREATED);

	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@PutMapping(value = "/update/{id}")
	@Transactional
	public ResponseEntity<Map<String, String>> updateViewer(org.springframework.ui.Model model,
			@Valid ViewerDTO viewerDTO, BindingResult bindingResult, RedirectAttributes redirect,
			HttpServletRequest httpServletRequest, @PathVariable("id") String id) {
		final Map<String, String> response = new HashMap<>();
		Viewer viewer = null;

		if (bindingResult.hasErrors()) {
			response.put(STATUS, ERROR_STATUS);
			response.put(CAUSE, utils.getMessage("ontology.validation.error", "validation error"));
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}
		viewer = viewerService.getViewerById(id, utils.getUserId());

		if (!utils.getUserId().equals(viewer.getUser().getUserId()) && !utils.getRole().equals("ROLE_ADMINISTRATOR")) {
			log.error(NOTPERMISSION);
			response.put(STATUS, ERROR_STATUS);
			response.put(CAUSE, NOTPERMISSION);
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}
		try {
			Boolean doRollback = (httpServletRequest.getParameter("rollback").equals("on")) ? true : false;
			if (doRollback) {
				// Serializa Viewer
				Rollback rollback = rollbackController.saveRollback(viewer, Rollback.EntityType.VIEWER);
				if (rollback == null) {
					response.put(STATUS, ERROR_STATUS);
					response.put(CAUSE, "Creation of rollback failed");
					return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
				}
			}

			viewer.setIdentification(viewerDTO.getIdentification());
			viewer.setDescription(viewerDTO.getDescription());
			viewer.setPublic(viewerDTO.getIsPublic());
			viewer.setJs(httpServletRequest.getParameter("jsViewer"));

			if (httpServletRequest.getParameter(LAYER_SELECTED_HIDDEN) != null) {
				Set<Layer> layersAux = viewer.getLayers();
				viewer.setLayers(new HashSet<Layer>());
				String[] split = httpServletRequest.getParameter(LAYER_SELECTED_HIDDEN).split(",");
				for (int i = 0; i < split.length; i++) {
					Layer layer = layerService.findByIdentification(split[i]);
					viewer.getLayers().add(layer);

					if (!layer.getViewers().contains(viewer)) {
						layer.getViewers().add(viewer);
					}
				}
				for (Layer l : layersAux) {
					if (!viewer.getLayers().contains(l)) {
						l.getViewers().remove(viewer);
					}
				}

			} else {
				for (Layer layer : viewer.getLayers()) {
					layer.getViewers().remove(viewer);
				}
				viewer.setLayers(new HashSet<Layer>());
			}

			viewerService.create(viewer, viewerDTO.getBaseLayer());

			response.put(REDIRECT, REDIRECT_CONTROLPANEL_VIEWERS_LIST);
			response.put(STATUS, OK_STATUS);
			return new ResponseEntity<>(response, HttpStatus.CREATED);

		} catch (Exception e) {
			response.put(STATUS, ERROR_STATUS);
			response.put(CAUSE, utils.getMessage("not found", "not found"));
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}
	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@GetMapping(value = "/update/{id}")
	public String update(Model model, @PathVariable("id") String id) {

		Viewer viewer = viewerService.getViewerById(id, utils.getUserId());

		if (!utils.getUserId().equals(viewer.getUser().getUserId()) && !utils.getRole().equals("ROLE_ADMINISTRATOR")) {
			log.error(NOTPERMISSION);
			return "error/403";
		}

		ViewerDTO viewerDTO = new ViewerDTO();
		viewerDTO.setId(viewer.getId());
		viewerDTO.setDescription(viewer.getDescription());
		viewerDTO.setIdentification(viewer.getIdentification());
		viewerDTO.setBaseLayer(viewer.getBaseLayer().getIdentification());
		viewerDTO.setIsPublic(viewer.isPublic());
		viewerDTO.setJs(viewer.getJs());

		model.addAttribute("viewer", viewerDTO);

		List<String> layers = new ArrayList<>();
		for (Layer layer : viewer.getLayers()) {
			layers.add(layer.getIdentification());
		}

		Map<String, String> layersTypes = layerService.getLayersTypes(utils.getUserId());
		model.addAttribute("layersTypes", layersTypes);
		model.addAttribute("tecnology", viewer.getBaseLayer().getTechnology());
		model.addAttribute("layersInUse", layers);
		return "viewers/create";
	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@GetMapping(value = "/doRollback/{id}")
	// @Transactional
	public @ResponseBody String doRollback(Model model, @PathVariable("id") String id) {

		try {

			Viewer viewerRollback = (Viewer) rollbackController.getRollback(id);
			Viewer viewer = viewerService.getViewerById(id, utils.getUserId());
			Set<Layer> layers = new HashSet<>();
			for (Layer layerRollback : viewerRollback.getLayers()) {
				Layer layer = layerService.findById(layerRollback.getId(), utils.getUserId());
				layer.getViewers().add(viewerRollback);
				layers.add(layer);

				if (layer.getViewers().contains(viewer)) {
					layer.getViewers().remove(viewer);
				}
			}

			viewerRollback.setLayers(layers);

			viewerService.create(viewerRollback, viewerRollback.getBaseLayer().getIdentification());

		} catch (Exception e) {
			log.error("Error in the serialization of the viewer. {}", e);
		}

		return REDIRECT_CONTROLPANEL_VIEWERS_LIST;

	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@DeleteMapping("/{id}")
	public String delete(Model model, @PathVariable("id") String id, RedirectAttributes redirect) {

		final Viewer viewer = viewerService.getViewerById(id, utils.getUserId());
		if (viewer != null) {
			try {

				for (Layer layer : viewer.getLayers()) {
					layer.getViewers().remove(viewer);
				}

				viewerService.deleteViewer(viewer, utils.getUserId());

			} catch (final Exception e) {
				utils.addRedirectMessageWithParam("ontology.delete.error", e.getMessage(), redirect);
				log.error("Error deleting viewer. ", e);
				return "redirect:/viewers/update/" + id;
			}
			return "redirect:/viewers/list";
		} else {
			return "redirect:/viewers/list";
		}
	}

	@GetMapping("/getBaseLayers/{technology}")
	public @ResponseBody List<BaseLayer> getBaseLayers(@PathVariable("technology") String technology) {

		return this.viewerService.getBaseLayersByTechnology(technology);
	}

	@GetMapping("/getLayers")
	public @ResponseBody List<String> getLayers() {
		return this.layerService.getAllIdentificationsByUser(utils.getUserId());
	}

	@GetMapping("/getLayerWms/{layer}")
	public @ResponseBody String getLayerWms(@PathVariable("layer") String layer) {
		return this.layerService.getLayerWms(layer);
	}

	@GetMapping("/getLayerKml/{layer}")
	public @ResponseBody String getLayerkml(@PathVariable("layer") String layer) {
		return this.layerService.getLayerKml(layer);
	}

	@GetMapping("/getLayerSvgImage/{layer}")
	public @ResponseBody String getLayerSvgImage(@PathVariable("layer") String layer) {
		return this.layerService.getLayerSvgImage(layer);
	}

	@GetMapping("/getQueryParamsAndRefresh/{layer}")
	public @ResponseBody String getQueryParamsAndRefresh(@PathVariable("layer") String layer) {
		return this.layerService.getQueryParamsAndRefresh(layer);
	}

	@GetMapping("/getJSBaseCode")
	public @ResponseBody String getJSBaseCode() {
		Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
		Map<String, Object> dataMap = new HashMap<>();

		try {
			TemplateLoader templateLoader = new ClassTemplateLoader(getClass(), "/viewers/templates");

			cfg.setTemplateLoader(templateLoader);
			Template baseJSViewerTemplate = cfg.getTemplate("baseJSViewerTemplate.ftl");

			dataMap.put("basePath", basePath);

			// write the freemarker output to a StringWriter
			StringWriter stringWriter = new StringWriter();
			baseJSViewerTemplate.process(dataMap, stringWriter);

			// get the String from the StringWriter
			return stringWriter.toString();
		} catch (IOException e) {
			log.error("Error configuring the template loader. {}", e.getMessage());
		} catch (TemplateException e) {
			log.error("Error processing the template loades. {}", e.getMessage());
		}
		return null;
	}

	@GetMapping(value = "/download/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<InputStreamResource> generateProject(@PathVariable("id") String id)
			throws FileNotFoundException {

		final File zipFile = viewerHelper.generateProject(id);

		final HttpHeaders respHeaders = new HttpHeaders();
		respHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		respHeaders.setContentDispositionFormData("attachment", zipFile.getName());
		respHeaders.setContentLength(zipFile.length());
		final InputStreamResource isr = new InputStreamResource(new FileInputStream(zipFile));
		return new ResponseEntity<>(isr, respHeaders, HttpStatus.OK);
	}

	@PreAuthorize("hasAnyRole('ROLE_ADMINISTRATOR','ROLE_DATASCIENTIST','ROLE_DEVELOPER')")
	@GetMapping(value = "/view/{id}", produces = "text/html")
	public String viewerViewer(Model model, @PathVariable("id") String id, HttpServletRequest request) {
		try {
			OAuth2AccessToken token = (OAuth2AccessToken) request.getSession().getAttribute("oauthToken");
			String userId = null;
			if (token != null) {
				userId = (String) token.getAdditionalInformation().get("principal");
			}

			Boolean hasPermission = viewerService.hasUserViewPermission(id, utils.getUserId(), userId);
			if (hasPermission != null && hasPermission) {
				model.addAttribute("js", viewerService.getViewerById(id, utils.getUserId()).getJs());
				return "viewers/view";
			} else if (hasPermission == null) {
				return REDIRECT_VIEWERS + id + LOGIN_PATH;
			} else {
				return REDIRECT_VIEWERS + id + LOGIN_PATH;
			}
		} catch (Exception e) {
			return REDIRECT_VIEWERS + id + LOGIN_PATH;
		}
	}

	@GetMapping("/getHtmlCode")
	public @ResponseBody String getHtmlCode() {
		Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
		Map<String, Object> dataMap = new HashMap<>();

		try {
			TemplateLoader templateLoader = new ClassTemplateLoader(getClass(), "/viewers/templates");

			cfg.setTemplateLoader(templateLoader);
			Template indexViewerTemplate = cfg.getTemplate("indexViewerTemplateAux.ftl");

			dataMap.put("cesiumPath", webProjectPath + "/cesium/Cesium1.60/Cesium.js");
			dataMap.put("widgetcss", webProjectPath + "/cesium/Cesium1.60/Widgets/widgets.css");
			dataMap.put("heatmap", webProjectPath + "/cesium/CesiumHeatmap/CesiumHeatmap.js");

			// write the freemarker output to a StringWriter
			StringWriter stringWriter = new StringWriter();
			indexViewerTemplate.process(dataMap, stringWriter);

			// get the String from the StringWriter
			return stringWriter.toString();
		} catch (IOException e) {
			log.error("Error configuring the template loader. {}", e.getMessage());
		} catch (TemplateException e) {
			log.error("Error processing the template loades. {}", e.getMessage());
		}
		return null;
	}

}
