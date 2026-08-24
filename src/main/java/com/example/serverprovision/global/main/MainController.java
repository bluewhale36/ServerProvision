package com.example.serverprovision.global.main;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

	@GetMapping({"", "/"})
	public String index() {
		return "redirect:/provisioning/server";
	}
}
